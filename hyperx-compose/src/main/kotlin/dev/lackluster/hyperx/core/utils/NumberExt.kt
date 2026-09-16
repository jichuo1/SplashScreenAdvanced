package dev.lackluster.hyperx.core.utils

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

fun Float.toDecimalString(decimals: Int = 2): String {
    if (decimals <= 0) return this.roundToLong().toString()
    val multiplier = when (decimals) {
        1 -> 10L
        2 -> 100L
        3 -> 1000L
        4 -> 10000L
        else -> 10.0.pow(decimals).toLong()
    }
    val totalValue = (this * multiplier).roundToLong()
    val integerPart = totalValue / multiplier
    val fractionalPart = abs(totalValue % multiplier)
    val sign = if (totalValue < 0 && integerPart == 0L) "-" else ""
    return "${sign}${integerPart}.${fractionalPart.toString().padStart(decimals, '0')}"
}

fun Double.toDecimalString(decimals: Int = 2): String {
    if (decimals <= 0) return this.roundToLong().toString()
    val multiplier = when (decimals) {
        1 -> 10L
        2 -> 100L
        3 -> 1000L
        4 -> 10000L
        else -> 10.0.pow(decimals).toLong()
    }
    val totalValue = (this * multiplier).roundToLong()
    val integerPart = totalValue / multiplier
    val fractionalPart = abs(totalValue % multiplier)
    val sign = if (totalValue < 0 && integerPart == 0L) "-" else ""
    return "${sign}${integerPart}.${fractionalPart.toString().padStart(decimals, '0')}"
}