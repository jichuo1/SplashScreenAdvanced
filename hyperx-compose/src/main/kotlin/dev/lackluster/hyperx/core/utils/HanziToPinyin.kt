package dev.lackluster.hyperx.core.utils

import android.icu.text.Transliterator

/**
 * 汉字转拼音，基于平台 ICU 的 Han-Latin 转换。
 */
object HanziToPinyin {
    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
    }

    @Synchronized
    fun toPinyin(input: String): String =
        transliterator.transliterate(input).replace(" ", "")
}
