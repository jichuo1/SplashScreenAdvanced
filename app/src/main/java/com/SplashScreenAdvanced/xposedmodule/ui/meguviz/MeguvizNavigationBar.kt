package com.SplashScreenAdvanced.xposedmodule.ui.meguviz

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.SplashScreenAdvanced.xposedmodule.R
import kotlin.math.roundToInt

/** BIL-style native dock: pager progress and selection semantics share one source of truth. */
class MeguvizNavigationBar(
    context: Context,
    titles: List<CharSequence>,
    iconResources: List<Int>,
    textColor: Int,
    selectedTextColor: Int,
    barBackground: Drawable,
    selectionBackground: Drawable,
    private val onSelect: (Int) -> Unit,
) : FrameLayout(context) {
    private val count = titles.size.also { require(it in 1..5 && iconResources.size == it) }
    private val selection = View(context).apply {
        id = R.id.native_bottom_selection
        background = selectionBackground
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val icons = ArrayList<ImageView>(count)
    private val labels = ArrayList<TextView>(count)
    private val renderedSteps = IntArray(count) { -1 }
    private val tintSteps = Array(33) {
        ColorStateList.valueOf(ColorUtils.blendARGB(textColor, selectedTextColor, it / 32f))
    }
    private var selectedPage = 0
    private var pagePosition = 0f

    init {
        id = R.id.native_bottom_navigation
        background = barBackground
        clipChildren = false
        clipToPadding = false
        addView(selection)
        repeat(count) { index ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = titles[index]
                setOnClickListener { onSelect(index) }
            }
            val icon = ImageView(context).apply {
                setImageResource(iconResources[index])
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = TextView(context).apply {
                text = titles[index]
                textSize = 12f
                gravity = Gravity.CENTER
                isSingleLine = true
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            item.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
            item.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
            icons += icon
            labels += label
            addView(item, LayoutParams(-2, -1))
        }
        setSelectedPage(0)
    }

    fun setSelectedPage(index: Int) {
        selectedPage = index.coerceIn(0, count - 1)
        repeat(count) { getChildAt(it + 1).isSelected = it == selectedPage }
        updateItemColors()
    }

    fun setPageProgress(position: Float) {
        pagePosition = position.coerceIn(0f, (count - 1).toFloat())
        updateSelectionPosition()
        updateItemColors()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val slotWidth = width / count
        selection.layout(0, dp(5), slotWidth, height - dp(5))
        repeat(count) { index ->
            val item = getChildAt(index + 1)
            item.layout(index * slotWidth, 0, (index + 1) * slotWidth, height)
        }
        updateSelectionPosition()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(dp(68), heightMeasureSpec)
        setMeasuredDimension(width, height)
        val slotWidth = width / count
        selection.measure(
            MeasureSpec.makeMeasureSpec(slotWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height - dp(10), MeasureSpec.EXACTLY),
        )
        repeat(count) { index ->
            getChildAt(index + 1).measure(
                MeasureSpec.makeMeasureSpec(slotWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
        }
    }

    private fun updateSelectionPosition() {
        if (width == 0) return
        selection.translationX = pagePosition * (width / count)
    }

    private fun updateItemColors() {
        repeat(count) { index ->
            val step = (32f * (1f - kotlin.math.abs(pagePosition - index).coerceAtMost(1f))).roundToInt()
            if (renderedSteps[index] == step) return@repeat
            renderedSteps[index] = step
            icons[index].imageTintList = tintSteps[step]
            labels[index].setTextColor(tintSteps[step])
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
