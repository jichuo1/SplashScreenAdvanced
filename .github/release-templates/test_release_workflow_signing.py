import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_DIRECTORY = REPOSITORY_ROOT / ".github" / "workflows"


class ReleaseWorkflowSigningTest(unittest.TestCase):
    def test_release_workflows_publish_only_fixed_signed_release_apks(self) -> None:
        for workflow_name in ("alpha-release.yml", "stable-release.yml"):
            with self.subTest(workflow=workflow_name):
                content = (WORKFLOW_DIRECTORY / workflow_name).read_text(encoding="utf-8")
                self.assertIn("assembleRelease", content)
                self.assertIn("app/build/outputs/apk/release/app-release.apk", content)
                self.assertIn("verify_release_apk.py", content)
                self.assertIn("apk_build_type=release", content)
                self.assertIn("apk_debuggable=false", content)
                self.assertIn("apk_signer_certificate_sha256", content)
                self.assertIn("minifyReleaseWithR8", content)
                self.assertIn("app/build/outputs/mapping/release/mapping.txt", content)
                self.assertIn("r8_mapping_id=", content)
                self.assertIn("r8_mapping_sha256=", content)
                self.assertIn("release-symbols", content)
                self.assertIn("actions/upload-artifact@v7", content)
                self.assertNotIn("app-debug.apk", content)
                publish_block = content.split("- name: Create GitHub", 1)[1].split(
                    "- name: Read back", 1
                )[0]
                self.assertNotIn("release-symbols", publish_block)
                self.assertNotIn("mapping-", publish_block)
                for secret_name in (
                    "ANDROID_SIGNING_KEY_BASE64",
                    "ANDROID_SIGNING_STORE_PASSWORD",
                    "ANDROID_SIGNING_KEY_ALIAS",
                    "ANDROID_SIGNING_KEY_PASSWORD",
                    "ANDROID_SIGNING_CERT_SHA256",
                ):
                    self.assertIn(f"secrets.{secret_name}", content)

    def test_workflows_use_current_action_generations(self) -> None:
        for workflow_name in ("alpha-release.yml", "stable-release.yml"):
            with self.subTest(workflow=workflow_name):
                content = (WORKFLOW_DIRECTORY / workflow_name).read_text(encoding="utf-8")
                self.assertIn("actions/checkout@v7", content)
                self.assertIn("android-actions/setup-android@v4", content)
                self.assertNotIn("actions/checkout@v4", content)
                self.assertNotIn("actions/checkout@v5", content)
                self.assertNotIn("actions/setup-java@v4", content)
                self.assertNotIn("actions/setup-java@v5", content)
                self.assertNotIn("android-actions/setup-android@v3", content)
                self.assertNotIn("actions/upload-artifact@v4", content)
                self.assertNotIn("actions/upload-artifact@v6", content)

        for workflow_name in ("alpha-release.yml", "stable-release.yml"):
            with self.subTest(release_workflow=workflow_name):
                content = (WORKFLOW_DIRECTORY / workflow_name).read_text(encoding="utf-8")
                self.assertIn("actions/setup-java@v6", content)
                self.assertIn("actions/upload-artifact@v7", content)

    def test_gradle_keeps_default_release_apk_filename(self) -> None:
        gradle_script = (
            REPOSITORY_ROOT / "app" / "build.gradle.kts"
        ).read_text(encoding="utf-8")
        self.assertNotIn("outputFileName", gradle_script)

    def test_alpha_does_not_cancel_in_progress_manual_publish(self) -> None:
        content = (WORKFLOW_DIRECTORY / "alpha-release.yml").read_text(encoding="utf-8")
        self.assertIn(
            "cancel-in-progress: ${{ github.event_name != 'workflow_dispatch' }}",
            content,
        )
        self.assertIn("github.event_name }}-${{ github.ref }}", content)
        self.assertIn("--continue", content)

    def test_stable_verify_keeps_continue_and_timeout(self) -> None:
        content = (WORKFLOW_DIRECTORY / "stable-release.yml").read_text(encoding="utf-8")
        self.assertIn("--continue", content)
        self.assertIn("timeout-minutes: 45", content)

    def test_lint_runs_in_parallel_and_gates_publishing(self) -> None:
        lint = (WORKFLOW_DIRECTORY / "android-lint.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_call:", lint)
        self.assertIn(":app:lintDebug", lint)
        self.assertIn("lint-reports-", lint)
        self.assertIn("cache-read-only: true", lint)
        for constant in ("CI_COMPILE_SDK_PACKAGE: 'platforms;android-37.2'", "CI_BUILD_TOOLS: '37.0.0'"):
            self.assertIn(constant, lint)
        for workflow_name in ("alpha-release.yml", "stable-release.yml"):
            with self.subTest(workflow=workflow_name):
                content = (WORKFLOW_DIRECTORY / workflow_name).read_text(encoding="utf-8")
                self.assertIn("uses: ./.github/workflows/android-lint.yml", content)
                self.assertIn("needs: [verify, lint]", content)
                self.assertIn("needs.lint.result == 'success'", content)
                # lint 只在并行作业里跑一次；verify 保留构建、测试与 R8。
                self.assertNotIn(":app:lintDebug", content)
                verify_block = content.split("\n  lint:\n", 1)[0]
                self.assertIn(":app:testDebugUnitTest", verify_block)
                self.assertIn(":app:minifyReleaseWithR8", verify_block)
                # R8 缓存键对齐：verify 带一次性签名身份，从不使用正式签名密钥。
                self.assertIn("verify-throwaway.p12", verify_block)
                self.assertNotIn("secrets.ANDROID_SIGNING", verify_block)
                for constant in ("CI_COMPILE_SDK_PACKAGE: 'platforms;android-37.2'", "CI_BUILD_TOOLS: '37.0.0'"):
                    self.assertIn(constant, content)

    def test_gradle_release_packaging_fails_without_complete_signing_identity(self) -> None:
        gradle_script = (
            REPOSITORY_ROOT / "app" / "build.gradle.kts"
        ).read_text(encoding="utf-8")
        self.assertIn('signingConfigs.create("fixedRelease")', gradle_script)
        self.assertIn("isDebuggable = false", gradle_script)
        self.assertIn("Release packaging requires the fixed signing identity", gradle_script)
        self.assertIn("SPLASH_SIGNING_STORE_FILE", gradle_script)
        self.assertIn('task.name.equals("packageRelease", ignoreCase = true)', gradle_script)
        self.assertNotIn("(assemble|bundle|package|sign)", gradle_script)


if __name__ == "__main__":
    unittest.main()
