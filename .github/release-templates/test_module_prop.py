from __future__ import annotations

from pathlib import Path
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
MODULE_PROP = REPOSITORY_ROOT / "app" / "src" / "main" / "resources" / "META-INF" / "xposed" / "module.prop"


def read_module_prop(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            raise ValueError(f"Malformed module.prop line: {raw_line}")
        properties[key.strip()] = value.strip()
    return properties


class ModulePropTest(unittest.TestCase):
    def test_autohotreload_requires_api_102(self) -> None:
        properties = read_module_prop(MODULE_PROP)
        min_api = int(properties["minApiVersion"])
        target_api = int(properties["targetApiVersion"])
        self.assertGreaterEqual(target_api, min_api)
        if properties.get("autoHotReload", "").lower() == "true":
            self.assertGreaterEqual(
                min_api,
                102,
                "autoHotReload is an API 102 feature; minApiVersion must be >= 102 "
                "or XposedNewApi lint fails the CI gate",
            )


if __name__ == "__main__":
    unittest.main()
