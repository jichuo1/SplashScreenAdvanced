#!/usr/bin/env python3
"""Capture read-only rotation/volume diagnostics immediately after manual reproduction."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGES = (
    "com.android.systemui",
    "miui.systemui.plugin",
    "com.SplashScreenAdvanced.xposedmodule",
)
PROPERTIES = (
    "ro.product.device",
    "ro.product.model",
    "ro.build.version.sdk",
    "ro.build.version.incremental",
)


def capture(command: list[str], timeout: int = 20) -> dict:
    try:
        result = subprocess.run(command, capture_output=True, timeout=timeout, shell=False)
        return {
            "command": command,
            "returncode": result.returncode,
            "stdout": result.stdout.decode("utf-8", errors="replace"),
            "stderr": result.stderr.decode("utf-8", errors="replace"),
        }
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"command": command, "returncode": None, "error": str(error)}


def plan() -> list[tuple[str, list[str]]]:
    # 音量窗口可能数秒后消失，窗口信息必须先于版本与日志。
    commands = [
        ("window", ["shell", "dumpsys", "window", "windows"]),
        ("display", ["shell", "dumpsys", "display"]),
        ("configuration", ["shell", "am", "get-config"]),
        ("systemui_services", ["shell", "dumpsys", "activity", "service", "com.android.systemui"]),
    ]
    for package in PACKAGES:
        commands.extend([
            (f"{package}_version", ["shell", "dumpsys", "package", package]),
            (f"{package}_paths", ["shell", "pm", "path", package]),
        ])
    return commands


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", required=True, help="Manual reproduction label; letters/digits/_/- only")
    parser.add_argument("--serial", help="ADB serial; required when more than one device is online")
    parser.add_argument("--adb", help="Path to adb executable")
    parser.add_argument("--expected-device", default="nabu", help="Refuse collection from a different device")
    parser.add_argument("--dry-run", action="store_true", help="Print collection plan without invoking adb")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.label):
        parser.error("--label must contain only letters, digits, _ or -, up to 80 characters")
    if args.dry_run:
        print(json.dumps({
            "expected_device": args.expected_device,
            "commands": plan(),
            "additional": ["selected device properties", "SystemUI PID and logcat (last 1500 lines)"],
            "output_root": str(ROOT / "build" / "rotation-diagnostics"),
        }, ensure_ascii=False, indent=2))
        return 0

    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root and os.name == "nt":
        sdk_root = str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk")
    sdk_adb = Path(sdk_root or "") / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    adb = args.adb or shutil.which("adb") or (str(sdk_adb) if sdk_adb.is_file() else None)
    if not adb:
        parser.error("adb not found; specify --adb or configure ANDROID_HOME")
    devices = capture([adb, "devices", "-l"])
    if devices.get("returncode") != 0:
        parser.error(f"ADB device listing failed: {devices.get('stderr') or devices.get('error')}")
    online = [line.split()[0] for line in devices["stdout"].splitlines()
              if re.match(r"^\S+\s+device(?:\s|$)", line)]
    serial = args.serial
    if serial is None:
        if len(online) != 1:
            parser.error("Exactly one online device is required, or choose one with --serial")
        serial = online[0]
    if serial not in online:
        parser.error("The selected device is not online/authorized")
    prefix = [adb, "-s", serial]
    identity = capture(prefix + ["shell", "getprop", "ro.product.device"])
    actual = identity.get("stdout", "").strip()
    if identity.get("returncode") != 0 or actual != args.expected_device:
        parser.error(f"Expected device {args.expected_device!r}; connected device is {actual!r}")

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = ROOT / "build" / "rotation-diagnostics" / f"{timestamp}_{args.label}"
    output.mkdir(parents=True, exist_ok=False)
    results: dict[str, dict] = {"identity": identity}
    for name, command in plan():
        results[name] = capture(prefix + command)
        (output / f"{name}.json").write_text(
            json.dumps(results[name], ensure_ascii=False, indent=2), encoding="utf-8"
        )
    for prop in PROPERTIES:
        results[prop] = capture(prefix + ["shell", "getprop", prop])
    pid = capture(prefix + ["shell", "pidof", "com.android.systemui"])
    results["systemui_pid"] = pid
    for value in pid.get("stdout", "").split():
        if value.isdecimal():
            results[f"systemui_log_{value}"] = capture(
                prefix + ["logcat", "-d", "--pid", value, "-t", "1500", "-v", "threadtime"]
            )
    (output / "capture.json").write_text(json.dumps({
        "captured_at_utc": timestamp,
        "label": args.label,
        "device": actual,
        "boundary": "Read-only dumps cannot prove internal ResourcesImpl ownership or volume child coordinates.",
        "results": results,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
