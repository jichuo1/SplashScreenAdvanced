#!/usr/bin/env python3
"""Ordered LLM provider slots for release-note generation (standard library only).

最多三个槽位，按 1 → 2 → 3 顺序尝试，每个槽位由以下环境变量描述：

    RELEASE_NOTES_LLM_<N>_API_KEY   必填；为空则跳过该槽位
    RELEASE_NOTES_LLM_<N>_PROTOCOL  anthropic（默认）或 openai（任意 OpenAI 兼容接口）
    RELEASE_NOTES_LLM_<N>_BASE_URL  可选；anthropic 默认 https://api.anthropic.com，
                                    openai 默认 https://api.openai.com/v1；私有代理地址应放 Secret
    RELEASE_NOTES_LLM_<N>_MODEL     anthropic 默认 claude-opus-5；openai 必填

结构化输出参数不被代理或兼容接口支持时自动逐级降级为纯提示词 JSON；所有密钥只进请求头，
不写日志。
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass


SLOT_COUNT = 3
DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com"
DEFAULT_ANTHROPIC_MODEL = "claude-opus-5"
DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
REQUEST_TIMEOUT_SECONDS = 300
MAX_OUTPUT_TOKENS = 16000
# 官方 API 上的服务端拒答回退：安全分类器拒答时由服务端换模型重跑，不经过代理时才启用。
ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"
ANTHROPIC_FALLBACK_MODELS = frozenset({"claude-opus-5", "claude-fable-5-1"})


class ProviderError(Exception):
    """Non-recoverable for this provider slot; the caller moves on to the next slot."""


class _RetryWithoutFormat(Exception):
    pass


@dataclass(frozen=True)
class ProviderSlot:
    index: int
    protocol: str
    base_url: str
    model: str
    api_key: str

    @property
    def label(self) -> str:
        return f"槽位 {self.index}（{self.protocol} · {self.model}）"


def load_slots(environ: dict[str, str] | None = None) -> tuple[list[ProviderSlot], list[str]]:
    """读取已配置的槽位；返回（可用槽位, 配置问题说明）。"""
    env = os.environ if environ is None else environ
    slots: list[ProviderSlot] = []
    problems: list[str] = []
    for index in range(1, SLOT_COUNT + 1):
        prefix = f"RELEASE_NOTES_LLM_{index}_"
        api_key = env.get(prefix + "API_KEY", "").strip()
        if not api_key:
            continue
        protocol = (env.get(prefix + "PROTOCOL", "").strip() or "anthropic").lower()
        base_url = env.get(prefix + "BASE_URL", "").strip().rstrip("/")
        model = env.get(prefix + "MODEL", "").strip()
        if protocol == "anthropic":
            base_url = base_url or DEFAULT_ANTHROPIC_BASE_URL
            model = model or DEFAULT_ANTHROPIC_MODEL
        elif protocol == "openai":
            base_url = base_url or DEFAULT_OPENAI_BASE_URL
            if not model:
                problems.append(f"槽位 {index} 使用 openai 协议但未设置 {prefix}MODEL，已跳过")
                continue
        else:
            problems.append(f"槽位 {index} 的 PROTOCOL={protocol!r} 无法识别，已跳过")
            continue
        slots.append(ProviderSlot(index, protocol, base_url, model, api_key))
    return slots, problems


def _post_json(url: str, headers: dict[str, str], payload: dict) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    last_error = ""
    for attempt in range(2):
        try:
            with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")[:500]
            if error.code == 400 and _mentions_format(detail):
                raise _RetryWithoutFormat(detail) from error
            if error.code in (408, 409, 429) or error.code >= 500:
                last_error = f"HTTP {error.code}: {detail}"
                time.sleep(5 * (attempt + 1))
                continue
            raise ProviderError(f"HTTP {error.code}: {detail}") from error
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = f"网络错误：{error}"
            time.sleep(5 * (attempt + 1))
        except ValueError as error:
            raise ProviderError(f"响应不是合法 JSON：{error}") from error
    raise ProviderError(last_error or "请求失败")


def _mentions_format(detail: str) -> bool:
    lowered = detail.lower()
    return any(key in lowered for key in ("output_config", "response_format", "json_schema", "format"))


def _anthropic_complete(slot: ProviderSlot, system: str, messages: list[dict], schema: dict | None) -> str:
    headers = {"x-api-key": slot.api_key, "anthropic-version": "2023-06-01"}
    payload: dict = {
        "model": slot.model,
        "max_tokens": MAX_OUTPUT_TOKENS,
        "system": system,
        "messages": messages,
    }
    if schema is not None:
        payload["output_config"] = {"format": {"type": "json_schema", "schema": schema}}
    if slot.base_url == DEFAULT_ANTHROPIC_BASE_URL and slot.model in ANTHROPIC_FALLBACK_MODELS:
        headers["anthropic-beta"] = ANTHROPIC_FALLBACK_BETA
        payload["fallbacks"] = "default"
    response = _post_json(f"{slot.base_url}/v1/messages", headers, payload)
    stop_reason = response.get("stop_reason")
    if stop_reason == "refusal":
        raise ProviderError(f"模型拒答：{response.get('stop_details')}")
    if stop_reason == "max_tokens":
        raise ProviderError("输出达到 max_tokens 上限被截断")
    text = "".join(
        block.get("text", "") for block in response.get("content", []) if block.get("type") == "text"
    )
    if not text.strip():
        raise ProviderError("响应中没有文本内容")
    return text


def _openai_complete(
    slot: ProviderSlot, system: str, messages: list[dict], response_format: dict | None
) -> str:
    payload: dict = {
        "model": slot.model,
        "messages": [{"role": "system", "content": system}, *messages],
    }
    if response_format is not None:
        payload["response_format"] = response_format
    response = _post_json(
        f"{slot.base_url}/chat/completions",
        {"Authorization": f"Bearer {slot.api_key}"},
        payload,
    )
    try:
        choice = response["choices"][0]
        text = choice["message"]["content"] or ""
    except (KeyError, IndexError, TypeError) as error:
        raise ProviderError(f"响应结构无法识别：{str(response)[:300]}") from error
    if choice.get("finish_reason") == "length":
        raise ProviderError("输出达到长度上限被截断")
    if not text.strip():
        raise ProviderError("响应中没有文本内容")
    return text


_DEFAULT_BASE_URLS = frozenset({DEFAULT_ANTHROPIC_BASE_URL, DEFAULT_OPENAI_BASE_URL})
# 服务端错误里可能回显（部分打码的）密钥；GitHub 只遮蔽完整 Secret，片段需要自己抹掉。
_SECRET_PATTERNS = (
    re.compile(r"\bsk-[A-Za-z0-9_\-*.]{6,}"),
    re.compile(r"(?i)(bearer\s+)[^\s\"',}]+"),
    re.compile(r"(?i)((?:api[_-]?key|x-api-key|token)[\"']?\s*[:=]\s*[\"']?)[^\s\"',}]+"),
)


def redact(text: str, slot: ProviderSlot) -> str:
    """日志与 Job Summary 在公开仓库中可见：抹掉密钥、密钥片段与非官方端点地址。"""
    if slot.api_key:
        text = text.replace(slot.api_key, "***")
    if slot.base_url not in _DEFAULT_BASE_URLS:
        host = urllib.parse.urlsplit(slot.base_url).netloc
        for secret in (slot.base_url, host):
            if secret:
                text = text.replace(secret, "<私有端点>")
    for pattern in _SECRET_PATTERNS:
        text = pattern.sub(lambda match: (match.group(1) if match.groups() else "") + "***", text)
    return text


def complete(slot: ProviderSlot, system: str, messages: list[dict], schema: dict) -> str:
    """调用一个槽位；错误信息在抛出前脱敏。"""
    try:
        return _complete(slot, system, messages, schema)
    except ProviderError as error:
        raise ProviderError(redact(str(error), slot)) from None


def _complete(slot: ProviderSlot, system: str, messages: list[dict], schema: dict) -> str:
    """结构化输出参数被拒时逐级降级，最终仍依赖提示词约束 JSON。"""
    if slot.protocol == "anthropic":
        formats: list = [schema, None]
        call = _anthropic_complete
    else:
        formats = [
            {"type": "json_schema", "json_schema": {"name": "release_notes", "schema": schema, "strict": True}},
            {"type": "json_object"},
            None,
        ]
        call = _openai_complete
    for option in formats:
        try:
            return call(slot, system, messages, option)
        except _RetryWithoutFormat as rejection:
            print(f"{slot.label} 不支持当前结构化输出参数，降级重试：{redact(str(rejection), slot)}", file=sys.stderr)
    raise ProviderError("所有输出格式均被拒绝")
