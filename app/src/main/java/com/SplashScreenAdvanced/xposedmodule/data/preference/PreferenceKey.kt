package com.SplashScreenAdvanced.xposedmodule.data.preference

/** A typed storage key, shared by the native settings UI and hook processes. */
class PreferenceKey<T : Any>(val name: String, val default: T) {
    override fun equals(other: Any?): Boolean = other is PreferenceKey<*> && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = "PreferenceKey(name='$name', default=$default)"
}
