#!/usr/bin/env python3
"""Generate user-facing release notes with an LLM chain and a deterministic fallback.

流程：收集净变化证据 → 按槽位顺序请求模型输出结构化 JSON → 本地校验（提交引用、覆盖率、
句数、术语、长度）→ 不通过则带错误重试一次 → 仍失败换下一槽位 → 全部失败回退规则版。
Markdown 一律由本脚本渲染，模型不直接产出排版，因此章节、图标、链接格式由代码保证。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

import generate_alpha_changelog
import generate_stable_changelog
import llm_providers
from release_context import ReleaseContext, build_context, load_config
from release_note_common import CATEGORY_ICONS, escape_markdown_text


TEMPLATES_DIR = Path(__file__).resolve().parent
OVERRIDE_DIR_NAME = "release-notes"
CATEGORY_KEYS = {"new": "新增", "fixed": "修复", "improved": "优化"}
MAX_ITEM_CHARS = 160
MAX_GROUP_CHARS = 16
MAX_SENTENCE_CHARS = 80
# 覆盖检查只在提交数适中时作为硬性规则；超大范围只记警告，避免反复重试。
STRICT_COVERAGE_LIMIT = 60
SENTENCE_SPLIT = re.compile(r"(?<=[。！？!?])")

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "array", "items": {"type": "string"}},
        "items": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "category": {"type": "string", "enum": list(CATEGORY_KEYS)},
                    "group": {"type": "string"},
                    "text": {"type": "string"},
                    "commits": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["category", "group", "text", "commits"],
                "additionalProperties": False,
            },
        },
        "maintenance": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["summary", "items", "maintenance"],
    "additionalProperties": False,
}


@dataclass
class NoteItem:
    category: str
    group: str
    text: str
    commits: list[str]


@dataclass
class ReleaseNotes:
    summary: list[str]
    items: list[NoteItem]
    maintenance: list[str]


@dataclass
class GenerationResult:
    changelog: str
    summary: str
    source: str
    log: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Prompt
# ---------------------------------------------------------------------------

def summary_rule(channel: str) -> str:
    if channel == "stable":
        return (
            f"summary 写 2～4 句完整中文句子（每句以。结尾，每句不超过 {MAX_SENTENCE_CHARS} 字），"
            "概括本版本带来的主要变化与价值，不堆砌实现细节。"
        )
    return "summary 可以为空数组；如有必要写 1～2 句，提示测试者本次重点验证什么。"


def style_section(config: dict) -> str:
    """维护者本人的文风：正式书面、适度宣传、概述概括而正文具体。"""
    example = config.get("summary_style_example", "").strip()
    example_block = (
        f"\n   维护者亲笔写的概述样例（只学习语气与句式，不要复用其中的内容）：\n   「{example}」"
        if example
        else ""
    )
    return f"""文风：
A. 使用正式书面语，措辞可以带适度的宣传感（如「全面焕新」「进一步优化」「显著提升」），
   但陈述的事实不能超出证据；不用感叹号，不称呼「你」「您」，不用口语和网络用语。
B. summary 一句聚焦一个方面，按「视觉与交互 → 性能 → 修复 → 新增」的顺序，没有的方面略过；
   第一句点出本版最核心的变化及其效果，可以用「得益于……，……」的句式。
C. summary 只做概括，可以用「诸多」「部分」「整体」等概括词，不列设置项名称和数字细节，细节交给 items；
   不要写固定的结尾套话（如「推荐升级体验」）。
D. 常用句式：「对……进行了优化」「新增了……功能，现可支持……」「以提升……与……」「进一步……」「……均已……」。{example_block}
E. group 用名词短语作小标题。item 可以用「要点：说明」的形式，允许补半句原理，但先写效果；
   只写效果不写实现机制（例如写「图标更清晰」，不写「改由 GPU 绘制、不再分配大块位图」）。
F. 设置项名称用「」括起，沿用界面原文。
G. 安装方式、重启生效、下载与反馈由发布模板负责，summary 和 items 都不要写。
H. 同一变化在 summary 与 items 中的分类和说法保持一致（例如 items 归为优化的，summary 不要说成新增）。"""


def build_system_prompt(config: dict, channel: str) -> str:
    style_notes = "\n".join(f"- {note}" for note in config.get("style_notes", []))
    forbidden = "、".join(config.get("forbidden_terms", []))
    channel_name = "Stable 正式版" if channel == "stable" else "Alpha 测试版"
    return f"""你负责为 {config["product_name"]} 撰写 {channel_name} 的 GitHub Release 更新说明。
产品背景：{config["product_context"]}

{style_section(config)}

写作规则：
1. 读者是普通用户。每条以用户能感知到的结果开头，必要时再补半句原因。不写类名、文件名、函数名、
   构建、CI、测试等内部细节。以下词语不得出现在 summary 和 items 中：{forbidden}。
2. 以「净变化」为准，「提交记录」只代表开发过程：
   - 同一功能分多次提交（第一阶段、收尾、补全、再优化）时合并成一条，只描述最终效果；
   - 在本次范围内新增后又被删除或回滚的内容不要写；
   - 已发布 Alpha 说明与净变化冲突时以净变化为准；不要把上一版本已有的功能写成新增。
3. category 取值：new=用户能用到的新功能或新选项；fixed=以前表现错误、现在恢复正常；
   improved=既有功能变得更快、更稳、更好看或兼容更多宿主版本。
4. 纯构建、CI、文档、测试、版本号、无可感知变化的重构，只把提交 SHA 放进 maintenance。
5. 每个提交都必须出现在某条 items[].commits 或 maintenance 中；commits 填 7 位短 SHA。
   一个提交做了多件事时拆成多条，并在每条里都引用它。
6. 相关条目用 group 聚合（不超过 {MAX_GROUP_CHARS} 个字的短标题），无需分组时 group 填空字符串。
   每条 text 为一句话，不超过 {MAX_ITEM_CHARS} 字，不换行，不使用 Markdown、链接、反引号或 @。
7. {summary_rule(channel)}
8. 只写证据里能看到的变化，不要猜测；拿不准的效果写得保守。
9. 使用简体中文，中文与英文、数字之间保留一个空格（如「所有 UI」「B 站 9.13.0 版本」）。
{style_notes}

只输出一个 JSON 对象，结构为：
{{"summary": ["句子"], "items": [{{"category": "new|fixed|improved", "group": "", "text": "", "commits": ["abc1234"]}}], "maintenance": ["abc1234"]}}"""


def build_user_prompt(context: ReleaseContext) -> str:
    previous = context.previous_tag or "（无，首个版本）"
    header = (
        f"本次发布：{context.release_tag}；对比基线：{previous}；"
        f"目标提交：{context.commit[:7]}；范围内非合并提交 {len(context.commits)} 个。"
    )
    if context.truncated:
        header += "\n部分证据因篇幅被截断，截断部分请勿推测。"
    return f"{header}\n\n{context.render()}"


# ---------------------------------------------------------------------------
# Parsing and validation
# ---------------------------------------------------------------------------

def extract_json(raw: str) -> dict:
    text = raw.strip()
    fenced = re.search(r"```(?:json)?\s*(\{.*\})\s*```", text, re.DOTALL)
    if fenced:
        text = fenced.group(1)
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("输出中找不到 JSON 对象")
    value = json.loads(text[start:end + 1])
    if not isinstance(value, dict):
        raise ValueError("顶层不是 JSON 对象")
    return value


def _forbidden_hits(text: str, terms: list[str]) -> list[str]:
    hits = []
    for term in terms:
        if term.isascii():
            if re.search(rf"(?<![A-Za-z0-9_]){re.escape(term)}(?![A-Za-z0-9_])", text):
                hits.append(term)
        elif term in text:
            hits.append(term)
    return hits


def _resolve_sha(short: str, full_shas: set[str]) -> str | None:
    short = short.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{7,40}", short):
        return None
    matches = [sha for sha in full_shas if sha.startswith(short)]
    return matches[0] if len(matches) == 1 else None


_CJK = r"[\u3400-\u4dbf\u4e00-\u9fff]"
_CJK_THEN_LATIN = re.compile(rf"({_CJK})([A-Za-z0-9])")
_LATIN_THEN_CJK = re.compile(rf"([A-Za-z0-9%])({_CJK})")
# 维护者文风：不感叹、不直接称呼读者。出现即要求模型重写。
_STYLE_VIOLATIONS = ((re.compile(r"[!！]"), "感叹号"), (re.compile(r"[你您]"), "「你/您」称呼"))


def space_cjk_latin(text: str) -> str:
    """中文与英文、数字之间补一个空格；由代码保证，不依赖模型遵守。"""
    return _LATIN_THEN_CJK.sub(r"\1 \2", _CJK_THEN_LATIN.sub(r"\1 \2", text))


def style_violations(text: str) -> list[str]:
    return [label for pattern, label in _STYLE_VIOLATIONS if pattern.search(text)]


def sentences(text: str) -> list[str]:
    return [part.strip() for part in SENTENCE_SPLIT.split(text) if part.strip()]


def validate_notes(
    payload: dict, context: ReleaseContext, config: dict
) -> tuple[ReleaseNotes | None, list[str], list[str]]:
    """返回（规范化结果, 硬性错误, 警告）。有硬性错误时结果为 None。"""
    errors: list[str] = []
    warnings: list[str] = []
    full_shas = context.commit_shas()
    forbidden = config.get("forbidden_terms", [])
    max_items = int(config.get("max_items", 18))

    summary_raw = payload.get("summary")
    items_raw = payload.get("items")
    maintenance_raw = payload.get("maintenance")
    if not isinstance(summary_raw, list) or not all(isinstance(s, str) for s in summary_raw):
        errors.append("summary 必须是字符串数组")
        summary_raw = []
    if not isinstance(items_raw, list):
        errors.append("items 必须是数组")
        items_raw = []
    if not isinstance(maintenance_raw, list) or not all(isinstance(s, str) for s in maintenance_raw):
        errors.append("maintenance 必须是字符串数组")
        maintenance_raw = []

    summary = [space_cjk_latin(s.strip()) for s in summary_raw if s.strip()]
    summary_sentences = sentences("".join(summary))
    if context.channel == "stable" and not 2 <= len(summary_sentences) <= 4:
        errors.append(f"Stable 的 summary 需要 2～4 句，当前 {len(summary_sentences)} 句")
    if context.channel == "alpha" and len(summary_sentences) > 2:
        errors.append(f"Alpha 的 summary 最多 2 句，当前 {len(summary_sentences)} 句")
    for sentence in summary_sentences:
        if len(sentence) > MAX_SENTENCE_CHARS:
            errors.append(f"summary 句子超过 {MAX_SENTENCE_CHARS} 字：{sentence[:30]}…")
    hits = _forbidden_hits(" ".join(summary), forbidden)
    if hits:
        errors.append(f"summary 含有内部术语：{'、'.join(hits)}")
    violations = style_violations("".join(summary))
    if violations:
        errors.append(f"summary 不符合文风，出现了{'、'.join(violations)}")

    covered: set[str] = set()
    items: list[NoteItem] = []
    seen_texts: set[str] = set()
    for position, raw_item in enumerate(items_raw, start=1):
        where = f"items[{position}]"
        if not isinstance(raw_item, dict):
            errors.append(f"{where} 不是对象")
            continue
        category = str(raw_item.get("category", "")).strip()
        group = space_cjk_latin(str(raw_item.get("group", "")).strip())
        text = space_cjk_latin(str(raw_item.get("text", "")).strip())
        commits = raw_item.get("commits", [])
        if category not in CATEGORY_KEYS:
            errors.append(f"{where}.category 非法：{category!r}")
        if not text:
            errors.append(f"{where}.text 为空")
            continue
        if len(text) > MAX_ITEM_CHARS:
            errors.append(f"{where}.text 超过 {MAX_ITEM_CHARS} 字：{text[:30]}…")
        if len(group) > MAX_GROUP_CHARS:
            errors.append(f"{where}.group 超过 {MAX_GROUP_CHARS} 字：{group}")
        if "\n" in text or re.search(r"`|https?://|\{\{|^[#>*\-]|\]\(", text):
            errors.append(f"{where}.text 含有换行、链接、反引号或 Markdown 标记：{text[:30]}…")
        hits = _forbidden_hits(f"{group} {text}", forbidden)
        if hits:
            errors.append(f"{where} 含有内部术语 {'、'.join(hits)}：{text[:30]}…")
        violations = style_violations(f"{group}{text}")
        if violations:
            errors.append(f"{where} 不符合文风，出现了{'、'.join(violations)}：{text[:30]}…")
        if text in seen_texts:
            errors.append(f"{where}.text 与前文重复：{text[:30]}…")
        seen_texts.add(text)
        resolved: list[str] = []
        if not isinstance(commits, list) or not commits:
            errors.append(f"{where}.commits 不能为空")
            commits = []
        for short in commits:
            sha = _resolve_sha(str(short), full_shas)
            if sha is None:
                errors.append(f"{where}.commits 引用了范围外或不唯一的提交：{short}")
            elif sha not in resolved:
                resolved.append(sha)
        covered.update(resolved)
        items.append(NoteItem(category, group, text, resolved))

    maintenance: list[str] = []
    for short in maintenance_raw:
        sha = _resolve_sha(short, full_shas)
        if sha is None:
            errors.append(f"maintenance 引用了范围外或不唯一的提交：{short}")
        else:
            maintenance.append(sha)
    covered.update(maintenance)

    if len(items) > max_items:
        errors.append(f"条目过多（{len(items)} 条，上限 {max_items}），请合并同类项")
    missing = [info for info in context.commits if info.sha not in covered]
    if missing:
        listing = "；".join(f"{info.sha[:7]} {info.subject}" for info in missing[:15])
        message = f"以下提交既没有写进 items 也没有列入 maintenance：{listing}"
        if len(context.commits) <= STRICT_COVERAGE_LIMIT:
            errors.append(message)
        else:
            warnings.append(message)
    if not items and len(maintenance) < len(context.commits):
        errors.append("items 为空，但并非所有提交都属于维护类")

    if errors:
        return None, errors, warnings
    return ReleaseNotes(summary, items, maintenance), errors, warnings


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------

def sanitize(text: str) -> str:
    # 模型文本只作为纯文本展示：转义代码标记、HTML 与 @ 提及，避免误通知或注入排版。
    text = escape_markdown_text(text)
    text = text.replace("<", "&lt;").replace(">", "&gt;")
    return text.replace("@", "@​")


def commit_link(sha: str, repository_url: str) -> str:
    return f"[`{sha[:7]}`]({repository_url}/commit/{sha})"


def render_changelog(notes: ReleaseNotes, context: ReleaseContext, repository_url: str) -> str:
    lines: list[str] = []
    if context.channel == "alpha" and notes.summary:
        lines.extend([sanitize("".join(notes.summary)), ""])

    for key, category in CATEGORY_KEYS.items():
        entries = [item for item in notes.items if item.category == key]
        if not entries:
            continue
        lines.extend([f"### {CATEGORY_ICONS[category]} {category}", ""])
        groups: dict[str, list[NoteItem]] = {}
        for item in entries:
            groups.setdefault(item.group, []).append(item)
        for group, grouped in sorted(groups.items(), key=lambda pair: pair[0] != ""):
            if group:
                lines.extend([f"**{sanitize(group)}**", ""])
            for item in grouped:
                line = f"- {sanitize(item.text)}"
                if context.channel == "alpha":
                    line += " (" + " · ".join(commit_link(sha, repository_url) for sha in item.commits) + ")"
                lines.append(line)
            lines.append("")

    if not notes.items:
        lines.extend(["- 本次以构建与维护调整为主，功能与界面没有可感知的变化。", ""])

    if context.commits:
        lines.extend(["<details>", f"<summary>📜 完整提交记录（{len(context.commits)}）</summary>", ""])
        for info in context.commits:
            lines.append(f"- {escape_markdown_text(info.subject)} ({commit_link(info.sha, repository_url)})")
        lines.extend(["", "</details>", ""])

    if context.previous_tag:
        lines.append(
            f"> [查看从 `{context.previous_tag}` 到本次提交的完整差异]"
            f"({repository_url}/compare/{context.previous_tag}...{context.commit})"
        )
    return "\n".join(lines).rstrip() + "\n"


# ---------------------------------------------------------------------------
# Orchestration
# ---------------------------------------------------------------------------

def fallback_summary(changelog: str) -> str:
    parts = []
    for name in CATEGORY_KEYS.values():
        match = re.search(rf"^### \S+ {name}\n\n((?:- .*\n)+)", changelog, re.MULTILINE)
        count = sum(1 for line in match.group(1).splitlines() if line != "- 无") if match else 0
        if count:
            parts.append(f"{count} 项{name}")
    detail = f"包含 {'、'.join(parts)}" if parts else "以构建与维护调整为主"
    return f"本版本{detail}，详见下方更新内容。建议所有用户覆盖安装升级。"


def fallback_changelog(channel: str, repo_root: Path, repository_url: str, release_tag: str, commit: str) -> str:
    module = generate_alpha_changelog if channel == "alpha" else generate_stable_changelog
    return module.build_changelog(repo_root, repository_url, release_tag, commit).rstrip() + "\n"


def generate_with_llm(
    context: ReleaseContext, config: dict, slots: list[llm_providers.ProviderSlot], log: list[str], warnings: list[str]
) -> tuple[ReleaseNotes, str] | None:
    system = build_system_prompt(config, context.channel)
    user_prompt = build_user_prompt(context)
    for slot in slots:
        messages: list[dict] = [{"role": "user", "content": user_prompt}]
        for attempt in (1, 2):
            try:
                raw = llm_providers.complete(slot, system, messages, OUTPUT_SCHEMA)
            except llm_providers.ProviderError as error:
                log.append(f"{slot.label} 第 {attempt} 次请求失败：{error}")
                break
            try:
                payload = extract_json(raw)
            except ValueError as error:
                notes, errors, slot_warnings = None, [f"输出不是合法 JSON：{error}"], []
            else:
                notes, errors, slot_warnings = validate_notes(payload, context, config)
            if notes is not None:
                log.append(f"{slot.label} 第 {attempt} 次输出通过校验")
                warnings.extend(slot_warnings)
                return notes, slot.label
            log.append(f"{slot.label} 第 {attempt} 次输出未通过校验：" + "；".join(errors[:8]))
            messages = [
                *messages,
                {"role": "assistant", "content": raw},
                {
                    "role": "user",
                    "content": "上一次输出未通过校验，请逐条修正后重新输出完整 JSON：\n"
                    + "\n".join(f"- {error}" for error in errors),
                },
            ]
    return None


def generate(
    *,
    channel: str,
    repo_root: Path,
    repository_url: str,
    repository: str,
    release_tag: str,
    commit: str,
    manual_summary: str = "",
    use_llm: bool = True,
    config: dict | None = None,
    slots: list[llm_providers.ProviderSlot] | None = None,
) -> GenerationResult:
    config = config if config is not None else load_config(TEMPLATES_DIR)
    repository_url = repository_url.rstrip("/")
    manual_summary = manual_summary.strip()
    log: list[str] = []
    warnings: list[str] = []

    override = repo_root / ".github" / OVERRIDE_DIR_NAME / f"{release_tag}.md"
    if override.is_file():
        changelog = override.read_text(encoding="utf-8").rstrip() + "\n"
        if channel == "stable" and not manual_summary:
            raise SystemExit(f"{override} 覆盖了更新内容，此时必须手动填写 release_summary。")
        return GenerationResult(changelog, manual_summary, f"手写覆盖文件 {override.name}", log, warnings)

    if use_llm:
        if slots is None:
            slots, problems = llm_providers.load_slots()
            warnings.extend(problems)
        if not slots:
            warnings.append("未配置任何 RELEASE_NOTES_LLM_<N>_API_KEY，直接使用规则版。")
        else:
            context = build_context(
                repo_root, config, channel=channel, release_tag=release_tag, commit=commit,
                repository=repository,
            )
            outcome = generate_with_llm(context, config, slots, log, warnings)
            if outcome is not None:
                notes, label = outcome
                summary = manual_summary or "".join(notes.summary)
                return GenerationResult(render_changelog(notes, context, repository_url), summary, label, log, warnings)
            warnings.append("所有模型槽位均失败，已回退为规则版，请检查下方记录并考虑手写覆盖。")

    changelog = fallback_changelog(channel, repo_root, repository_url, release_tag, commit)
    summary = manual_summary or (fallback_summary(changelog) if channel == "stable" else "")
    return GenerationResult(changelog, summary, "规则版", log, warnings)


def render_report(result: GenerationResult, channel: str, release_tag: str) -> str:
    lines = [f"### Release notes · {release_tag} ({channel})", "", f"- 生成来源：**{result.source}**"]
    lines.extend(f"- ⚠️ {warning}" for warning in result.warnings)
    if result.log:
        lines.extend(["", "<details><summary>模型调用记录</summary>", ""])
        lines.extend(f"- {entry}" for entry in result.log)
        lines.extend(["", "</details>"])
    if result.summary:
        lines.extend(["", "#### 版本概述", "", result.summary])
    lines.extend(["", "#### 更新内容预览", "", result.changelog])
    return "\n".join(lines) + "\n"


def repository_from_url(repository_url: str) -> str:
    match = re.search(r"github\.com[/:]([^/]+/[^/]+?)(?:\.git)?/?$", repository_url)
    return match.group(1) if match else ""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--channel", required=True, choices=("alpha", "stable"))
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--repository-url", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--changelog-output", required=True, type=Path)
    parser.add_argument("--summary-output", type=Path)
    parser.add_argument("--report-output", type=Path)
    parser.add_argument("--summary", default="", help="Manual Stable summary; overrides the generated one")
    parser.add_argument("--no-llm", action="store_true", help="Skip every provider and use the rule-based notes")
    args = parser.parse_args()

    pattern = generate_alpha_changelog.ALPHA_TAG_PATTERN if args.channel == "alpha" else generate_stable_changelog.STABLE_TAG_PATTERN
    if pattern.fullmatch(args.release_tag) is None:
        raise SystemExit(f"Invalid {args.channel} tag: {args.release_tag}")

    result = generate(
        channel=args.channel,
        repo_root=args.repo_root.resolve(),
        repository_url=args.repository_url,
        repository=repository_from_url(args.repository_url),
        release_tag=args.release_tag,
        commit=args.commit,
        manual_summary=args.summary,
        use_llm=not args.no_llm,
    )
    if args.channel == "stable" and not result.summary.strip():
        raise SystemExit("Stable release summary is empty.")

    args.changelog_output.parent.mkdir(parents=True, exist_ok=True)
    args.changelog_output.write_text(result.changelog, encoding="utf-8", newline="\n")
    if args.summary_output is not None:
        args.summary_output.write_text(result.summary.strip() + "\n", encoding="utf-8", newline="\n")
    if args.report_output is not None:
        with args.report_output.open("a", encoding="utf-8", newline="\n") as report:
            report.write(render_report(result, args.channel, args.release_tag))
    for warning in result.warnings:
        print(f"::warning title=Release notes::{warning}")
    print(f"Release notes source: {result.source}", file=sys.stderr)


if __name__ == "__main__":
    main()
