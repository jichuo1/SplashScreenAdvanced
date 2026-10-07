package com.SplashScreenAdvanced.xposedmodule.update

import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown.Kind
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown.Style
import org.junit.Assert.*
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @Test fun actualReleaseStructureKeepsDetailsAndDownloadTableWithoutHtmlWrappers() {
        val source = """
            <div align="center">

            ## 🧬 SplashScreenAdvanced v1.0.5

            _✨ 更新说明_

            </div>
            <details>
            <summary>📜 完整提交记录（4）</summary>

            - fix(icon): 修复图标 ([`abc123`](https://github.com/jichuo1/SplashScreenAdvanced/commit/abc123))
            </details>

            | 文件 | 说明 |
            | --- | --- |
            | `module.apk` | SHA-256 `12345` |
        """.trimIndent()
        val blocks = ReleaseNotesMarkdown.parse(source)
        assertEquals(listOf(Kind.HEADING, Kind.PARAGRAPH, Kind.HEADING, Kind.LIST, Kind.TABLE), blocks.map { it.kind })
        assertTrue(blocks[0].centered)
        assertTrue(blocks[1].centered)
        assertFalse(blocks[3].centered)
        assertEquals("📜 完整提交记录（4）", blocks[2].content.text)
        assertEquals("module.apk", blocks.last().rows[1][0].text)
        assertEquals("SHA-256 12345", blocks.last().rows[1][1].text)
        val link = blocks[3].content.ranges.single { it.style == Style.LINK }
        assertEquals("abc123", blocks[3].content.text.substring(link.start, link.end))
        assertTrue(blocks[3].content.ranges.any { it.style == Style.CODE && it.start == link.start && it.end == link.end })
    }

    @Test fun nestedEmphasisAndLinksUseOffsetsInRenderedText() {
        val value = ReleaseNotesMarkdown.inline("**修复 *图标*** 与 [**下载**](https://example.com/a_(b))")
        assertEquals("修复 图标 与 下载", value.text)
        val link = value.ranges.single { it.style == Style.LINK }
        assertEquals("下载", value.text.substring(link.start, link.end))
        assertEquals("https://example.com/a_(b)", link.destination)
        assertTrue(value.ranges.any { it.style == Style.ITALIC && value.text.substring(it.start, it.end) == "图标" })
    }

    @Test fun codeBlocksRetainLiteralMarkupAndBlankLines() {
        val blocks = ReleaseNotesMarkdown.parse("```kotlin\n**literal**\n\nval x = 1\n```\n\n# 标题")
        assertEquals(Kind.CODE, blocks[0].kind)
        assertEquals("**literal**\n\nval x = 1", blocks[0].content.text)
        assertTrue(blocks[0].content.ranges.isEmpty())
        assertEquals(Kind.HEADING, blocks[1].kind)
    }

    @Test fun escapesCodeAndPackageUnderscoresArePreserved() {
        val value = ReleaseNotesMarkdown.inline("\\*literal\\* `**code**` package_name_test")
        assertEquals("*literal* **code** package_name_test", value.text)
        assertEquals(listOf(Style.CODE), value.ranges.map { it.style })
    }

    @Test fun unsafeLinksAndImagesNeverBecomeClickable() {
        val value = ReleaseNotesMarkdown.inline("[bad](javascript:alert(1)) ![图片](https://example.com/a.png) [app](intent://open) [safe](https://example.com/page)")
        assertEquals("bad 图片 app safe", value.text)
        assertEquals(listOf("https://example.com/page"), value.ranges.filter { it.style == Style.LINK }.map { it.destination })
        assertNull(ReleaseNotesMarkdown.safeLink("https://user:pass@example.com/"))
        assertNull(ReleaseNotesMarkdown.safeLink("file:///sdcard/a"))
    }

    @Test fun tablePipesInsideCodeAndEscapedPipesStayInTheirCells() {
        val table = ReleaseNotesMarkdown.parse("| 文件 | 说明 |\n| :--- | ---: |\n| `a|b` | a\\|b |").single()
        assertEquals(2, table.rows[1].size)
        assertEquals("a|b", table.rows[1][0].text)
        assertEquals("a|b", table.rows[1][1].text)
    }

    @Test fun listsTasksAndQuotesRetainTheirStructure() {
        val blocks = ReleaseNotesMarkdown.parse("- 一级\n  - 二级\n1. 编号\n- [x] 完成\n- [ ] 待办\n\n> 引用\n> 第二行\n\n---")
        assertEquals(listOf(0, 1, 0, 0, 0), blocks.take(5).map { it.level })
        assertEquals(listOf("• 一级", "• 二级", "1. 编号", "☑ 完成", "☐ 待办"), blocks.take(5).map { it.content.text })
        assertEquals("引用\n第二行", blocks[5].content.text)
        assertEquals(Kind.RULE, blocks.last().kind)
    }

    @Test fun emptyAndUnclosedSyntaxRemainReadable() {
        assertTrue(ReleaseNotesMarkdown.parse("\n \n").isEmpty())
        assertEquals("**未闭合 [链接]", ReleaseNotesMarkdown.inline("**未闭合 [链接]").text)
        assertEquals("<script>alert(1)</script>", ReleaseNotesMarkdown.inline("<script>alert(1)</script>").text)
        assertEquals("未闭合代码", ReleaseNotesMarkdown.parse("~~~\n未闭合代码").single().content.text)
    }

    @Test(timeout = 2000) fun sourceLimitSizedMarkersAndCodeCannotOverflowOrStallParsing() {
        assertEquals(Kind.RULE, ReleaseNotesMarkdown.parse("-".repeat(32768)).single().kind)
        val code = "`".repeat(32768)
        assertEquals(code, ReleaseNotesMarkdown.inline(code).text)
    }

    @Test fun githubTipIsRenderedAsAQuotedLabelInsteadOfRawAlertSyntax() {
        val block = ReleaseNotesMarkdown.parse("> [!TIP]\n> 覆盖安装后需重启系统界面生效。").single()
        assertEquals(Kind.QUOTE, block.kind)
        assertEquals("💡 TIP\n覆盖安装后需重启系统界面生效。", block.content.text)
        assertTrue(block.content.ranges.any { it.style == Style.BOLD })
    }
}
