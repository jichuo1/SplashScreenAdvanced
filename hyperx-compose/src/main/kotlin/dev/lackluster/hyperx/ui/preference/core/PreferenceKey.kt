package dev.lackluster.hyperx.ui.preference.core

class PreferenceKey<T: Any>(
    val name: String,
    val default: T
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreferenceKey<*>) return false
        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "PreferenceKey(name='$name', default=$default)"
    }
}