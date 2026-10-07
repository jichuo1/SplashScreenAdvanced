package com.SplashScreenAdvanced.xposedmodule.utils

import android.icu.text.Transliterator

/** Platform ICU transliteration, retaining the search behavior of the previous UI. */
object HanziToPinyin {
    private val transliterator by lazy { Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower") }
    @Synchronized
    fun toPinyin(input: String): String = transliterator.transliterate(input).replace(" ", "")
}
