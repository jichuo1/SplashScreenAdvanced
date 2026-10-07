package com.SplashScreenAdvanced.xposedmodule.ui.nativeview

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown.Block
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown.Kind
import com.SplashScreenAdvanced.xposedmodule.utils.update.ReleaseNotesMarkdown.Style

/** Keep text in a single native layout per table-delimited section, even for long announcements. */
internal fun NativePageUi.releaseNotes(source: String): LinearLayout = column().apply {
    clipChildren = true
    clipToPadding = true
    val section = mutableListOf<Block>()
    fun flush() {
        if (section.isEmpty()) return
        val rendered = SpannableStringBuilder()
        section.forEach { block ->
            if (rendered.isNotEmpty()) rendered.append(if (block.kind == Kind.LIST) "\n" else "\n\n")
            val start = rendered.length
            rendered.append(if (block.kind == Kind.RULE) "────────────" else styledInline(block.content))
            val end = rendered.length
            if (end > start) {
                fun span(value: Any) = rendered.setSpan(value, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                when (block.kind) {
                    Kind.HEADING -> { span(StyleSpan(Typeface.BOLD)); span(RelativeSizeSpan(if (block.level <= 2) 1.35f else 1.15f)) }
                    Kind.CODE -> { span(TypefaceSpan("monospace")); span(BackgroundColorSpan(codeColor())) }
                    Kind.QUOTE -> span(QuoteSpan(palette.primary, dp(3), dp(10)))
                    Kind.LIST -> span(LeadingMarginSpan.Standard(dp(8 + block.level.coerceAtMost(6) * 12)))
                    else -> Unit
                }
                if (block.centered) span(android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER))
            }
        }
        addView(notesText().apply { text = rendered }, LinearLayout.LayoutParams(-1, -2))
        section.clear()
    }
    ReleaseNotesMarkdown.parse(source).forEach { block ->
        if (block.kind == Kind.TABLE) {
            flush()
            // An unusually wide/large table must not create thousands of native cell Views.
            if (block.rows.any { it.size > 6 } || block.rows.sumOf { it.size } > 256) {
                val rendered = SpannableStringBuilder()
                block.rows.forEach { cells ->
                    if (rendered.isNotEmpty()) rendered.append("\n\n")
                    cells.forEachIndexed { index, cell ->
                        if (index > 0) rendered.append(" | ")
                        rendered.append(styledInline(cell))
                    }
                }
                addView(notesText().apply { text = rendered }, LinearLayout.LayoutParams(-1, -2))
                return@forEach
            }
            addView(TableLayout(activity).apply {
                isShrinkAllColumns = true
                isStretchAllColumns = true
                val columns = block.rows.maxOfOrNull { it.size } ?: 0
                block.rows.forEachIndexed { index, cells ->
                    addView(TableRow(activity).apply {
                        repeat(columns) { column ->
                            addView(notesText().apply {
                                textSize = 13f
                                text = cells.getOrNull(column)?.let(::styledInline) ?: ""
                                if (index == 0) setTypeface(typeface, Typeface.BOLD)
                                setPadding(dp(6), dp(8), dp(6), dp(8))
                                setBackgroundColor(if (index == 0) codeColor() else android.graphics.Color.TRANSPARENT)
                            }, TableRow.LayoutParams(0, -2, 1f))
                        }
                    })
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })
        } else section += block
    }
    flush()
}

private fun NativePageUi.notesText() = text("", 14f).apply {
    setLineSpacing(dp(3).toFloat(), 1f)
    setLinkTextColor(palette.primary)
    movementMethod = LinkMovementMethod.getInstance()
    gravity = Gravity.START
}

private fun NativePageUi.codeColor(): Int = (palette.primary and 0x00ffffff) or 0x18000000

private fun NativePageUi.styledInline(value: ReleaseNotesMarkdown.Inline): CharSequence = SpannableStringBuilder(value.text).apply {
    value.ranges.forEach { range ->
        val span = when (range.style) {
            Style.BOLD -> StyleSpan(Typeface.BOLD)
            Style.ITALIC -> StyleSpan(Typeface.ITALIC)
            Style.STRIKE -> StrikethroughSpan()
            Style.CODE -> TypefaceSpan("monospace")
            Style.LINK -> object : ClickableSpan() {
                override fun onClick(widget: View) { range.destination?.let(::openUrl) }
                override fun updateDrawState(ds: TextPaint) { ds.color = palette.primary; ds.isUnderlineText = true }
            }
        }
        setSpan(span, range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (range.style == Style.CODE) setSpan(BackgroundColorSpan(codeColor()), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
