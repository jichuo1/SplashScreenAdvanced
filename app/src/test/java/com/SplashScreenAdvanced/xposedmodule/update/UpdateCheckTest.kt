package com.SplashScreenAdvanced.xposedmodule.update

import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker.ReleaseVersion
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker.UpdateChannel
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesSourcePolicy
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckCoordinator
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    // ---------- ReleaseVersion.parse ----------

    @Test
    fun `parse accepts stable tags with and without v prefix`() {
        assertEquals(ReleaseVersion(1, 0, 3, null), ReleaseVersion.parse("1.0.3"))
        assertEquals(ReleaseVersion(1, 0, 3, null), ReleaseVersion.parse("v1.0.3"))
        assertEquals(ReleaseVersion(12, 34, 567, null), ReleaseVersion.parse("v12.34.567"))
    }

    @Test
    fun `parse accepts alpha tags`() {
        assertEquals(ReleaseVersion(1, 0, 4, 1), ReleaseVersion.parse("v1.0.4-alpha.1"))
        assertEquals(ReleaseVersion(1, 0, 4, 12), ReleaseVersion.parse("1.0.4-alpha.12"))
    }

    @Test
    fun `parse rejects unsupported tag shapes`() {
        listOf(
            "", "v1.0", "1.0.3.4", "v1.0.3-beta.1", "v1.0.3-rc1", "v1.0.3-alpha",
            "alpha", "v.x.y.z", "v1.0.3-alpha.x", "  ", "v1.0.3 alpha.1"
        ).forEach { tag ->
            assertNull("expected null for <$tag>", ReleaseVersion.parse(tag))
        }
    }

    // ---------- ReleaseVersion ordering ----------

    @Test
    fun `stable ranks above alpha of same base version`() {
        assertTrue(
            ReleaseVersion.parse("v1.0.3")!! > ReleaseVersion.parse("v1.0.3-alpha.2")!!
        )
        assertTrue(
            ReleaseVersion.parse("v1.0.3-alpha.2")!! > ReleaseVersion.parse("v1.0.3-alpha.1")!!
        )
        assertTrue(
            ReleaseVersion.parse("v1.0.4-alpha.1")!! > ReleaseVersion.parse("v1.0.3")!!
        )
    }

    // ---------- compareVersions / isNewerVersion ----------

    @Test
    fun `compareVersions yields three-way relation`() {
        assertEquals(
            GitHubReleaseChecker.VersionRelation.REMOTE_NEWER,
            GitHubReleaseChecker.compareVersions("v1.0.4", "1.0.3")
        )
        assertEquals(
            GitHubReleaseChecker.VersionRelation.EQUAL,
            GitHubReleaseChecker.compareVersions("v1.0.3", "1.0.3")
        )
        assertEquals(
            GitHubReleaseChecker.VersionRelation.LOCAL_NEWER,
            GitHubReleaseChecker.compareVersions("v1.0.3-alpha.1", "1.0.3")
        )
        assertNull(GitHubReleaseChecker.compareVersions("not-a-version", "1.0.3"))
        assertNull(GitHubReleaseChecker.compareVersions("v1.0.4", "garbage"))
    }

    @Test
    fun `isNewerVersion fails closed on malformed input`() {
        assertTrue(GitHubReleaseChecker.isNewerVersion("v1.0.4", "1.0.3"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("v1.0.3", "1.0.3"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("v1.0.2", "1.0.3"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("garbage", "1.0.3"))
        assertFalse(GitHubReleaseChecker.isNewerVersion("v1.0.4", "garbage"))
    }

    // ---------- UpdateChannel ----------

    @Test
    fun `unknown stored channel falls back to stable`() {
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorageValue(null))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorageValue(""))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorageValue("nightly"))
        assertEquals(UpdateChannel.STABLE, UpdateChannel.fromStorageValue("stable"))
        assertEquals(UpdateChannel.PREVIEW, UpdateChannel.fromStorageValue("preview"))
    }

    // ---------- validateGitHubUrl ----------

    @Test
    fun `validateGitHubUrl accepts repository release urls`() {
        val url = GitHubReleaseChecker.validateGitHubUrl(
            "https://github.com/jichuo1/SplashScreenAdvanced/releases/tag/v1.0.3",
            "release page"
        )
        assertEquals(
            "https://github.com/jichuo1/SplashScreenAdvanced/releases/tag/v1.0.3",
            url
        )
        GitHubReleaseChecker.validateGitHubUrl(
            "https://github.com/jichuo1/SplashScreenAdvanced/releases/download/v1.0.3/app.apk",
            "APK asset"
        )
    }

    @Test
    fun `validateGitHubUrl rejects foreign and non-https urls`() {
        listOf(
            "http://github.com/jichuo1/SplashScreenAdvanced/releases/tag/v1.0.3",
            "https://github.com/other/repo/releases/tag/v1.0.3",
            "https://evil.com/jichuo1/SplashScreenAdvanced/releases/tag/v1.0.3",
            "https://github.com/jichuo1/SplashScreenAdvanced",
            "https://user:pw@github.com/jichuo1/SplashScreenAdvanced/releases/tag/v1.0.3",
            "not a url"
        ).forEach { url ->
            try {
                GitHubReleaseChecker.validateGitHubUrl(url, "test")
                org.junit.Assert.fail("expected IOException for <$url>")
            } catch (_: java.io.IOException) {
            }
        }
    }

    // ---------- UpdateCheckCoordinator races ----------

    @Test
    fun `coordinator serializes and drops stale channel results`() {
        val coordinator = UpdateCheckCoordinator()

        val first = coordinator.submit(
            UpdateCheckManager.Request(UpdateChannel.STABLE, manual = false)
        )
        assertEquals(UpdateChannel.STABLE, first?.channel)

        // 同渠道重复请求不再启动
        assertNull(
            coordinator.submit(UpdateCheckManager.Request(UpdateChannel.STABLE, true))
        )

        // 完成时用户已切到 PREVIEW → 结果不投递
        val completion = coordinator.complete(UpdateChannel.STABLE, UpdateChannel.PREVIEW)
        assertFalse(completion.shouldDeliverResult)
        assertNull(completion.nextRequest)
    }

    @Test
    fun `coordinator delivers result when channel unchanged`() {
        val coordinator = UpdateCheckCoordinator()
        coordinator.submit(UpdateCheckManager.Request(UpdateChannel.STABLE, manual = true))
        val completion = coordinator.complete(UpdateChannel.STABLE, UpdateChannel.STABLE)
        assertTrue(completion.shouldDeliverResult)
        assertNull(completion.nextRequest)
        assertFalse(coordinator.isBusy())
    }

    @Test
    fun `coordinator queues manual channel-switch request`() {
        val coordinator = UpdateCheckCoordinator()
        coordinator.submit(UpdateCheckManager.Request(UpdateChannel.STABLE, manual = false))

        // 用户切渠道后手动检查 → 排队等待
        assertNull(
            coordinator.submit(UpdateCheckManager.Request(UpdateChannel.PREVIEW, true))
        )
        assertTrue(coordinator.isBusy())

        // 旧渠道结果不投递，排队的 PREVIEW 请求被启动
        val completion = coordinator.complete(UpdateChannel.STABLE, UpdateChannel.PREVIEW)
        assertFalse(completion.shouldDeliverResult)
        assertEquals(UpdateChannel.PREVIEW, completion.nextRequest?.channel)

        val done = coordinator.complete(UpdateChannel.PREVIEW, UpdateChannel.PREVIEW)
        assertTrue(done.shouldDeliverResult)
        assertFalse(coordinator.isBusy())
    }

    // ---------- ReleaseNotesSourcePolicy ----------

    @Test
    fun `release notes pass through when short`() {
        val result = ReleaseNotesSourcePolicy.prepare("## What's new\n\n- fix bugs")
        assertFalse(result.truncated)
        assertTrue(result.markdown.contains("fix bugs"))
    }

    @Test
    fun `release notes strip control characters`() {
        val result = ReleaseNotesSourcePolicy.prepare("a\u0007b\rc\n")
        assertFalse(result.truncated)
        result.markdown.forEach {
            assertTrue(it == '\n' || it == '\t' || !Character.isISOControl(it))
        }
        assertEquals("ab\nc", result.markdown)
    }

    @Test
    fun `release notes are truncated within bound`() {
        val long = "x\n\n".repeat(50_000)
        val result = ReleaseNotesSourcePolicy.prepare(long)
        assertTrue(result.truncated)
        assertTrue(result.markdown.length <= ReleaseNotesSourcePolicy.MAX_MARKDOWN_LENGTH + 8)
    }
}
