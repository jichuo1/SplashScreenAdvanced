package dev.lackluster.hyperx.ui.theme

/**
 * Module-app chrome style. Widget trees stay on Miuix; this switches color, shape,
 * indication, overscroll, blur, and type to Material You or HyperOS/MIUI.
 */
enum class UiStyle(val prefValue: Int) {
    Miuix(0),
    MaterialYou(1);

    val isMaterialYou: Boolean get() = this == MaterialYou
    val isMiuix: Boolean get() = this == Miuix

    companion object {
        fun fromPref(value: Int): UiStyle =
            entries.firstOrNull { it.prefValue == value } ?: Miuix
    }
}
