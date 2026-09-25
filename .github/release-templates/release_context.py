#!/usr/bin/env python3
"""Collect the evidence an LLM needs to write user-facing release notes.

只收集「净变化」与「过程记录」两类证据：提交标题只说明过程，净 diff（尤其是用户可见文案）
才代表发布时的最终状态。模型据此合并阶段性提交、剔除已被推翻的中间实现。
"""

from __future__ import annotations

import fnmatch
import json
import os
import re
import subprocess
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path


CONFIG_FILE_NAME = "release_notes_config.json"
EMPTY_TREE_SHA = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
ALPHA_TAG_PATTERN = re.compile(r"^v(\d+)\.(\d+)\.(\d+)-alpha\.(\d+)$")
STABLE_TAG_PATTERN = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
# 发布正文里模板自带的外壳：标题区、下载表、反馈段。只保留中间的变更内容作为参考。
_TEMPLATE_HEADER_END = re.compile(r"</div>\s*", re.MULTILINE)


@dataclass(frozen=True)
class CommitInfo:
    sha: str
    date: str
    subject: str
    body: str
    files_changed: int
    insertions: int
    deletions: int
    top_files: tuple[str, ...]


@dataclass
class ReleaseContext:
    channel: str
    release_tag: str
    previous_tag: str | None
    commit: str
    commits: list[CommitInfo]
    sections: list[tuple[str, str]] = field(default_factory=list)
    truncated: bool = False

    def commit_shas(self) -> set[str]:
        return {commit.sha for commit in self.commits}

    def render(self) -> str:
        return "\n\n".join(f"## {title}\n\n{body}" for title, body in self.sections)


def load_config(templates_dir: Path) -> dict:
    return json.loads((templates_dir / CONFIG_FILE_NAME).read_text(encoding="utf-8"))


def run_git(repo_root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_root,
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    return result.stdout


def _parse_tag(tag: str, pattern: re.Pattern[str]) -> tuple[int, ...] | None:
    match = pattern.fullmatch(tag)
    return tuple(int(part) for part in match.groups()) if match else None


def find_previous_tag(repo_root: Path, commit: str, release_tag: str, channel: str) -> str | None:
    """Alpha 对比上一个 Alpha，Stable 对比上一个版本号更小的 Stable。"""
    pattern = ALPHA_TAG_PATTERN if channel == "alpha" else STABLE_TAG_PATTERN
    current = _parse_tag(release_tag, pattern)
    tags = run_git(repo_root, "tag", "--merged", commit, "--list", "v[0-9]*").splitlines()
    candidates = []
    for tag in (tag.strip() for tag in tags):
        parsed = _parse_tag(tag, pattern)
        if parsed is None or tag == release_tag:
            continue
        if channel == "stable" and current is not None and parsed >= current:
            continue
        candidates.append((parsed, tag))
    return max(candidates)[1] if candidates else None


def list_commits(repo_root: Path, revision_range: str) -> list[CommitInfo]:
    # 记录分隔符避免提交正文里的换行和制表符破坏解析。
    raw = run_git(
        repo_root,
        "log",
        "--reverse",
        "--no-merges",
        "--date=short",
        "--format=%x1e%H%x1f%ad%x1f%s%x1f%b%x1f",
        "--numstat",
        revision_range,
    )
    commits: list[CommitInfo] = []
    for record in raw.split("\x1e"):
        if not record.strip():
            continue
        parts = record.split("\x1f")
        if len(parts) < 5:
            continue
        sha, date, subject, body, numstat = parts[:5]
        churn: list[tuple[int, str]] = []
        insertions = deletions = 0
        for line in numstat.strip().splitlines():
            columns = line.split("\t")
            if len(columns) != 3:
                continue
            added, removed, path = columns
            added_count = int(added) if added.isdigit() else 0
            removed_count = int(removed) if removed.isdigit() else 0
            insertions += added_count
            deletions += removed_count
            churn.append((added_count + removed_count, path))
        churn.sort(reverse=True)
        commits.append(
            CommitInfo(
                sha=sha.strip(),
                date=date.strip(),
                subject=subject.strip(),
                body=body.strip(),
                files_changed=len(churn),
                insertions=insertions,
                deletions=deletions,
                top_files=tuple(path for _, path in churn[:5]),
            )
        )
    return commits


def short_path(path: str, prefixes: list[str]) -> str:
    """去掉配置里的公共包路径前缀，节省上下文预算。"""
    for prefix in prefixes:
        if path.startswith(prefix):
            return path[len(prefix):]
    return path


def _matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def changed_lines_only(diff_text: str) -> str:
    """只保留增删行与所属文件名，去掉 diff 头和上下文，压缩文案类 diff。"""
    lines: list[str] = []
    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            lines.append(f"# {line[6:]}")
        elif line.startswith(("+++", "---", "diff --git", "index ", "@@")):
            continue
        elif line.startswith(("+", "-")) and line[1:].strip():
            lines.append(line)
    return "\n".join(lines)


_STRING_ENTRY = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.DOTALL)


def load_string_entries(repo_root: Path, revision: str, path: str) -> dict[str, str] | None:
    """读取某个版本的 Android 字符串资源；文件不存在时返回 None。"""
    result = subprocess.run(
        ["git", "show", f"{revision}:{path}"],
        cwd=repo_root,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        return None
    return {name: " ".join(value.split()) for name, value in _STRING_ENTRY.findall(result.stdout)}


def diff_string_resources(
    repo_root: Path, base: str, head: str, path: str, fallbacks: list[str]
) -> str:
    """按资源名比较文案：新增、修改、删除各自列出，不受条目重排或文件迁移影响。"""
    current = load_string_entries(repo_root, head, path)
    if current is None:
        return ""
    previous = load_string_entries(repo_root, base, path)
    note = ""
    if previous is None:
        # 文案迁移到新路径（例如引入多语言后中文从 values/ 挪到 values-zh-rCN/）时，改用旧路径做基线。
        for fallback in fallbacks:
            previous = load_string_entries(repo_root, base, fallback)
            if previous is not None:
                note = f"（基线中不存在该文件，改用基线的 {fallback} 对比）"
                break
    if previous is None:
        previous = {}
        note = "（基线中不存在该文件，以下全部为新增）"
    added = [f"+ {name} = {value}" for name, value in current.items() if name not in previous]
    changed = [
        f"~ {name}：{previous[name]} → {value}"
        for name, value in current.items()
        if name in previous and previous[name] != value
    ]
    removed = [f"- {name} = {value}" for name, value in previous.items() if name not in current]
    if not (added or changed or removed):
        return ""
    return "\n".join([f"# {path}{note}", *added, *changed, *removed])


def strip_release_template(body: str) -> str:
    """去掉发布正文的模板外壳，只保留变更记录部分给模型当风格参考。"""
    header = _TEMPLATE_HEADER_END.search(body)
    if header is not None:
        body = body[header.end():]
    body = body.split("\n---\n", 1)[0]
    body = re.sub(r"^> \[!WARNING\]\n(?:>.*\n?)*", "", body, flags=re.MULTILINE)
    return body.strip()


def fetch_release_body(repository: str, tag: str) -> str | None:
    """读取已发布 Release 的正文；无令牌、网络失败或不存在时返回 None。"""
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token or not repository:
        return None
    api = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    request = urllib.request.Request(
        f"{api}/repos/{repository}/releases/tags/{tag}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "release-notes-generator",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8")).get("body") or None
    except (urllib.error.URLError, TimeoutError, ValueError):
        return None


def _alpha_tags_since(repo_root: Path, commit: str, previous_stable: str | None) -> list[str]:
    tags = run_git(repo_root, "tag", "--merged", commit, "--list", "v[0-9]*-alpha.*").splitlines()
    result = []
    for tag in (tag.strip() for tag in tags):
        if ALPHA_TAG_PATTERN.fullmatch(tag) is None:
            continue
        if previous_stable is not None:
            is_old = subprocess.run(
                ["git", "merge-base", "--is-ancestor", tag, previous_stable],
                cwd=repo_root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode == 0
            if is_old:
                continue
        result.append(tag)
    return sorted(result, key=lambda tag: _parse_tag(tag, ALPHA_TAG_PATTERN) or ())


def _clip(text: str, limit: int) -> tuple[str, bool]:
    if len(text) <= limit:
        return text, False
    return text[:limit].rstrip() + "\n…（已截断）", True


def build_context(
    repo_root: Path,
    config: dict,
    *,
    channel: str,
    release_tag: str,
    commit: str,
    repository: str = "",
    fetch_published: bool = True,
) -> ReleaseContext:
    previous_tag = find_previous_tag(repo_root, commit, release_tag, channel)
    revision_range = f"{previous_tag}..{commit}" if previous_tag else commit
    commits = list_commits(repo_root, revision_range)
    context = ReleaseContext(channel, release_tag, previous_tag, commit, commits)
    budget = int(config.get("max_context_chars", 60000))
    strip_prefixes = config.get("path_strip_prefixes", [])

    def add(title: str, body: str, share: int) -> None:
        nonlocal budget
        if not body.strip() or budget <= 0:
            return
        clipped, truncated = _clip(body, min(share, budget))
        context.truncated |= truncated
        context.sections.append((title, clipped))
        budget -= len(clipped)

    # 1. 提交过程记录：按时间先后排列，模型据此识别「阶段一 → 收尾」「新增后又撤回」。
    commit_lines = []
    for info in commits:
        line = (
            f"- {info.sha[:7]} {info.date} {info.subject} "
            f"（{info.files_changed} 个文件，+{info.insertions}/-{info.deletions}）"
        )
        if info.body:
            line += "\n  正文：" + info.body.replace("\n", " ")[:400]
        if info.top_files:
            line += "\n  主要文件：" + ", ".join(path.rsplit("/", 1)[-1] for path in info.top_files)
        commit_lines.append(line)
    add("提交记录（时间正序，只代表开发过程）", "\n".join(commit_lines) or "（无提交）", 20000)

    # 没有上一标签时对 Git 空树做 diff，等价于完整快照。
    diff_base = previous_tag or EMPTY_TREE_SHA

    # 2. 用户可见文案的净变化：这是最终状态，优先级最高。
    user_text_paths = config.get("user_text_paths", [])
    fallbacks = config.get("user_text_fallbacks", {})
    text_diffs = [
        diff_string_resources(repo_root, diff_base, commit, path, fallbacks.get(path, []))
        for path in user_text_paths
    ]
    add(
        "用户可见文案净变化（以此为准；+ 新增、~ 修改、- 删除）",
        "\n\n".join(diff for diff in text_diffs if diff),
        16000,
    )

    highlight_paths = config.get("highlight_paths", [])
    if highlight_paths:
        highlight_diff = run_git(repo_root, "diff", "-U1", diff_base, commit, "--", *highlight_paths)
        add("内置更新亮点目录净变化", changed_lines_only(highlight_diff), 6000)

    # 3. 净文件变化统计，帮助判断改动集中在哪些模块。
    ignored = config.get("ignored_path_patterns", [])
    numstat = run_git(repo_root, "diff", "--numstat", diff_base, commit)
    churn: list[tuple[int, str]] = []
    for line in numstat.splitlines():
        columns = line.split("\t")
        if len(columns) == 3 and not _matches(columns[2], ignored):
            added, removed, path = columns
            total = (int(added) if added.isdigit() else 0) + (int(removed) if removed.isdigit() else 0)
            churn.append((total, path))
    churn.sort(reverse=True)
    add(
        "净文件变化（按改动量排序）",
        "\n".join(f"- {short_path(path, strip_prefixes)} ({total})" for total, path in churn[:80]),
        6000,
    )

    # 4. Stable：期间已发布的 Alpha 正文与上一 Stable 正文，作为已对外说明过的内容和风格参考。
    if channel == "stable" and fetch_published:
        published = []
        for tag in _alpha_tags_since(repo_root, commit, previous_tag):
            body = fetch_release_body(repository, tag)
            if body:
                published.append(f"### {tag}\n\n{strip_release_template(body)}")
        add("本周期已发布的 Alpha 说明（可能过时，与净变化冲突时以净变化为准）", "\n\n".join(published), 12000)
        if previous_tag:
            body = fetch_release_body(repository, previous_tag)
            if body:
                add(f"上一 Stable {previous_tag} 的正文（仅作语气与粒度参考，不要复述其内容）",
                    strip_release_template(body), 5000)

    # 5. 剩余预算给源码 diff 摘录，按改动量从大到小，每个文件限长。
    source_prefixes = tuple(config.get("source_prefixes", []))
    excerpts = []
    for _, path in churn:
        if budget - sum(len(part) for part in excerpts) < 2000:
            break
        if source_prefixes and not path.startswith(source_prefixes):
            continue
        if path in user_text_paths or path in highlight_paths:
            continue
        patch = run_git(repo_root, "diff", "-U1", diff_base, commit, "--", path)
        excerpt, _ = _clip(changed_lines_only(patch).replace(path, short_path(path, strip_prefixes)), 3000)
        if excerpt:
            excerpts.append(excerpt)
    add("源码净变化摘录（按改动量排序，已截断）", "\n\n".join(excerpts), budget)
    return context
