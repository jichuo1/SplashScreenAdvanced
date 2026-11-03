package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Magnifier
import androidx.palette.graphics.Palette
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeChoice
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.INPUT_HEIGHT_DP
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.ROW_GAP_DP
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.engineEditText
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.engineSlider
import com.lumen.coacervation.engine.motion.pager.LumenPageScrollView
import com.SplashScreenAdvanced.xposedmodule.utils.toMap
import com.SplashScreenAdvanced.xposedmodule.utils.toSet
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.lumen.coacervation.engine.interaction.ElasticInteractionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

fun NativePageUi.buildColorPickerPage(packageName: String): View {
    val container = column()
    val loading = text(string(R.string.loading))
    container.addView(loading)
    activity.uiScope.launch {
        val config = withContext(Dispatchers.IO) { runCatching { AppColorConfig(packageName, activity, repo) } }.getOrNull()
        if (config == null) { loading.text = string(R.string.color_input_invalid); return@launch }
        activity.updatePageTitle(config.appName)
        val editor = colorEditor(config)
        container.removeAllViews()
        container.addView(editor, LinearLayout.LayoutParams(-1, -1))
    }
    return container
}

private fun NativePageUi.colorEditor(config: AppColorConfig): View {
    var dark = restored?.getBoolean("colorDark") ?: activity.isDark()
    var color = restored?.getInt("color", config.getDefaultBGColor(dark)) ?: config.getDefaultBGColor(dark)
    val hsv = restored?.getFloatArray("colorHsv")?.takeIf { it.size == 3 }?.clone() ?: FloatArray(3).also { Color.colorToHSV(color, it) }
    val root = column()
    val content = column().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
    val scroll = LumenPageScrollView(activity, onUserScroll = { activity.cancelReveal() }, onContentTouch = {}).apply {
        addView(content)
        clipToPadding = false
        onScrollPositionChanged = { view -> lumen.notifyScrollPositionChanged(view) }
    }
    root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    stageChanges({ color != config.getDefaultBGColor(dark) }, { color = config.getDefaultBGColor(dark); Color.colorToHSV(color, hsv); refresh() })
    addStateWriter { it.putBoolean("colorDark", dark); it.putInt("color", color); it.putFloatArray("colorHsv", hsv.clone()); it.putInt("scrollY", scroll.scrollY) }
    scroll.post { scroll.scrollTo(0, restored?.getInt("scrollY") ?: 0) }
    // Rounded like the cards around it; a hairline keeps a white or black preview from melting into the page.
    val previewFill = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(color)
        setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(palette.textPrimary, 0x1F))
    }
    val preview = FrameLayout(activity).apply {
        background = previewFill
        clipToOutline = true
        addView(ImageView(activity).apply {
            setImageBitmap(config.appIcon)
            tag = ElasticInteractionController.EXCLUDED_TAG
            val magnifier = Magnifier.Builder(this).setSize(dp(100), dp(100)).setInitialZoom(5f).build()
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        magnifier.show(event.x, event.y, event.x, event.y - dp(80))
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        magnifier.dismiss(); view.parent.requestDisallowInterceptTouchEvent(false)
                        if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    }
                }
                true
            }
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) { magnifier.dismiss() }
            })
        }, FrameLayout.LayoutParams(dp(72), dp(72), android.view.Gravity.CENTER))
    }
    content.addView(preview, LinearLayout.LayoutParams(-1, dp(168)))
    bind { previewFill.setColor(color) }
    fun applyColor(value: Int, syncHsv: Boolean = true) { color = value; if (syncHsv) Color.colorToHSV(color, hsv); refresh() }
    // Fixed round swatches: weighted slots used to stretch them into ovals as wide as a seventh of the screen.
    val swatches = LinearLayout(activity).apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(4), 0, dp(4), 0)
    }
    content.addView(swatches, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(12) })
    activity.uiScope.launch {
        val colors = withContext(Dispatchers.Default) {
            val palette = Palette.from(config.appIcon).resizeBitmapArea(48 * 48).maximumColorCount(8).generate()
            listOf(palette.getDominantColor(0), palette.getLightVibrantColor(0), palette.getVibrantColor(0),
                palette.getDarkVibrantColor(0), palette.getLightMutedColor(0), palette.getMutedColor(0),
                palette.getDarkMutedColor(0)).distinct().filter { it != 0 }
        }
        colors.forEach { sample ->
            swatches.addView(View(activity).apply {
                contentDescription = "#%06X".format(Locale.ROOT, sample and 0xFFFFFF)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(sample)
                    setStroke(dp(1), androidx.core.graphics.ColorUtils.setAlphaComponent(palette.textPrimary, 0x1F))
                }
                setOnClickListener { applyColor(sample) }
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        }
    }
    with(content) {
        addSetting(choice(R.string.target_color_mode, {
            listOf(NativeChoice(false, string(R.string.light_color)), NativeChoice(true, string(R.string.dark_color)))
        }, { dark }, R.string.target_color_mode_tips, moduleRequired = false) { target ->
            fun switchMode() { dark = target; applyColor(config.getDefaultBGColor(dark)) }
            if (!confirmDiscard { switchMode() }) switchMode()
        })
        val manual = action(string(R.string.manual_input), moduleRequired = false) { anchor ->
            dialog(string(R.string.manual_input), string(R.string.manual_input_hint), anchor) { dialog, body ->
                val input = engineEditText(EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT
                    setText("%06X".format(Locale.ROOT, color and 0xFFFFFF)); setSelectAllOnFocus(true)
                    setTextColor(palette.textPrimary)
                    isSingleLine = true
                    setPadding(dp(16), 0, dp(16), 0)
                })
                body.addView(input, LinearLayout.LayoutParams(-1, dp(INPUT_HEIGHT_DP)).apply { topMargin = dp(16) })
                dialogActions(body,
                    button(string(R.string.button_cancel)) { activity.modals.dismiss(dialog, body) },
                    button(string(android.R.string.ok), filled = true) {
                        val value = runCatching { Color.parseColor("#" + input.text.toString().trim().removePrefix("#")) }.getOrNull()
                        if (value == null) input.error = string(R.string.color_input_invalid)
                        else activity.modals.dismiss(dialog, body) { applyColor(value) }
                    })
            }
        }
        bind { manual.valueView.text = "#%06X".format(Locale.ROOT, color and 0xFFFFFF) }
        addSetting(manual)
        addSetting(action(string(R.string.reset), moduleRequired = false) {
            if (config.isConfiguringOverallBGColor) {
                val key = if (dark) Preferences.Background.OVERALL_BG_COLOR_NIGHT else Preferences.Background.OVERALL_BG_COLOR
                write(key, key.default)
            } else {
                val key = if (dark) Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK else Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP
                val values = repo.get(key).toMap(); values.remove(config.packageName); write(key, values.toSet())
            }
            config.reloadDefaults(); applyColor(config.getDefaultBGColor(dark))
        })
        val rgb = column()
        val hsvControls = column()
        // A two-way segmented switch: the active colour space sits on the selected surface, like a choice in a dialog.
        val tabs = LinearLayout(activity).apply { clipChildren = false; clipToPadding = false }
        var hsvShown = restored?.getBoolean("colorHsvShown") ?: false
        addStateWriter { it.putBoolean("colorHsvShown", hsvShown) }
        fun segment(label: Int, hsvSegment: Boolean) = android.widget.TextView(activity).apply {
            text = string(label)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            isClickable = true
            setOnClickListener { hsvShown = hsvSegment; refresh() }
        }
        val rgbTab = segment(R.string.rgb_color_space, false)
        val hsvTab = segment(R.string.hsv_color_space, true)
        tabs.addView(rgbTab, LinearLayout.LayoutParams(0, dp(44), 1f))
        tabs.addView(hsvTab, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) })
        bind {
            rgb.visibility = if (hsvShown) View.GONE else View.VISIBLE
            hsvControls.visibility = if (hsvShown) View.VISIBLE else View.GONE
            listOf(rgbTab to !hsvShown, hsvTab to hsvShown).forEach { (tab, selected) ->
                lumen.styleSelectionControl(tab, 14f, selected)
                tab.setTextColor(if (selected) palette.primary else palette.textSecondary)
            }
        }
        addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })
        val fields = listOf(R.string.rgb_r, R.string.rgb_g, R.string.rgb_b)
        fields.forEachIndexed { index, title ->
            val shift = (2 - index) * 8
            rgb.addSetting(channel(title, 255, { (color shr shift) and 255 }) { value ->
                applyColor((color and (255 shl shift).inv()) or (value shl shift))
            })
        }
        val plane = SaturationValueView(this@colorEditor) { saturation, value ->
            hsv[1] = saturation; hsv[2] = value
            applyColor(Color.HSVToColor(hsv), syncHsv = false)
        }
        // Same corner as the cards; the outline clips the gradient square.
        plane.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, dp(16).toFloat())
        }
        plane.clipToOutline = true
        hsvControls.addView(plane, LinearLayout.LayoutParams(-1, dp(200)).apply { bottomMargin = dp(ROW_GAP_DP) })
        bind { plane.setColor(hsv[0], hsv[1], hsv[2]) }
        listOf(R.string.hue, R.string.saturation, R.string.value).forEachIndexed { index, title ->
            val max = if (index == 0) 360 else 100
            hsvControls.addSetting(channel(title, max, {
                (hsv[index] * if (index == 0) 1f else 100f).toInt()
            }) { value ->
                hsv[index] = value / if (index == 0) 1f else 100f
                applyColor(Color.HSVToColor(hsv), syncHsv = false)
            })
        }
        addSetting(rgb); addSetting(hsvControls)
    }
    // Page foot: undo the edit on the left, save on the right, on the same 48dp buttons and 8dp gutter as the dialogs.
    val revert = button(string(R.string.reset)) { applyColor(config.getDefaultBGColor(dark)) }
    val save = button(string(R.string.save), filled = true) {
        val hex = "#%06X".format(Locale.ROOT, color and 0xFFFFFF)
        val success = if (config.isConfiguringOverallBGColor) {
            write(if (dark) Preferences.Background.OVERALL_BG_COLOR_NIGHT else Preferences.Background.OVERALL_BG_COLOR, hex)
        } else {
            val key = if (dark) Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP_DARK else Preferences.AppList.INDIVIDUAL_BG_COLOR_APP_MAP
            val values = repo.get(key).toMap(); values[config.packageName] = hex; write(key, values.toSet())
        }
        if (success) { config.reloadDefaults(); applyColor(config.getDefaultBGColor(dark)); activity.toast(R.string.save_successful) }
    }.apply { id = R.id.native_save }
    root.addView(buttonRow(revert, save).apply { setPadding(dp(16), dp(8), dp(16), dp(12)) }, LinearLayout.LayoutParams(-1, -2))
    return root
}

private fun NativePageUi.channel(title: Int, maximum: Int, current: () -> Int, changed: (Int) -> Unit): View = column().apply {
    setPadding(dp(18), dp(14), dp(18), dp(10))
    background = lumen.cardBackground(palette.surface, 16f)
    // Channel name on the left, its value on the right in fixed-width digits so the line does not jitter while dragging.
    val heading = LinearLayout(activity).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
    heading.addView(text(string(title), 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
    val reading = text("", 13f, secondary = true).apply { fontFeatureSettings = "tnum" }
    heading.addView(reading, LinearLayout.LayoutParams(-2, -2))
    addView(heading, LinearLayout.LayoutParams(-1, -2))
    // The engine's slider: it follows the active material and handles magnet, stretch and haptics itself.
    val seek = engineSlider(0f, maximum.toFloat(), 1f, 0f, string(title)).apply {
        setOnValueChangeListener { _, value, fromUser -> if (fromUser) changed(value.toInt()) }
    }
    addView(seek, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    bind { seek.setValue(current().coerceIn(0, maximum).toFloat(), animate = false); reading.text = "${current()} / $maximum" }
}

private class SaturationValueView(ui: NativePageUi, private val changed: (Float, Float) -> Unit) : View(ui.activity) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = ui.dp(2).toFloat(); color = Color.WHITE
    }
    private val hueScratch = floatArrayOf(0f, 1f, 1f)
    private var hue = -1f
    private var saturation = 1f
    private var value = 1f
    private var horizontal: Shader? = null
    private var vertical: Shader? = null
    init { tag = ElasticInteractionController.EXCLUDED_TAG; contentDescription = ui.string(R.string.hsv_color_space) }
    fun setColor(h: Float, s: Float, v: Float) {
        if (hue != h) { hue = h; updateShaders() }
        saturation = s; value = v; invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateShaders() }
    private fun updateShaders() {
        if (width <= 0 || height <= 0) return
        hueScratch[0] = hue.coerceAtLeast(0f)
        horizontal = LinearGradient(0f, 0f, width.toFloat(), 0f, Color.WHITE, Color.HSVToColor(hueScratch), Shader.TileMode.CLAMP)
        vertical = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas: Canvas) {
        paint.shader = horizontal; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = vertical; canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.drawCircle(saturation * width, (1f - value) * height, 12f * resources.displayMetrics.density, marker)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            parent.requestDisallowInterceptTouchEvent(true)
            changed((event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f), (1f - event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent.requestDisallowInterceptTouchEvent(false)
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
