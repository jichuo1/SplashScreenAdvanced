package dev.lackluster.hyperx.core

import android.content.SharedPreferences
import androidx.core.content.edit

object SafeSP {
    @Volatile
    var mSP: SharedPreferences? = null

    fun setSP(sharedPreferences: SharedPreferences?) {
        mSP = sharedPreferences
    }

    fun getSP(): SharedPreferences? {
        return mSP
    }

    fun containsKey(key: String, defValue: Boolean = false): Boolean {
        return mSP?.all?.containsKey(key) ?: defValue
    }

    fun putAny(key: String, any: Any) {
        mSP?.edit {
            when (any) {
                is Boolean -> putBoolean(key, any)
                is String -> putString(key, any)
                is Int -> putInt(key, any)
                is Float -> putFloat(key, any)
                is Long -> putLong(key, any)
            }
        }
    }

    fun putStringSet(key: String, set: Set<String>) {
        mSP?.edit {
            putStringSet(key, set)
        }
    }

    fun getBoolean(key: String, defValue: Boolean = false): Boolean {
        return mSP?.getBoolean(key, defValue) ?: defValue
    }

    fun getInt(key: String, defValue: Int = 0): Int {
        return mSP?.getInt(key, defValue) ?: defValue
    }

    fun getFloat(key: String, defValue: Float = 0.0f): Float {
        return mSP?.getFloat(key, defValue) ?: defValue
    }

    fun getLong(key: String, defValue: Long = 0L): Long {
        return mSP?.getLong(key, defValue) ?: defValue
    }

    fun getString(key: String, defValue: String = ""): String {
        return mSP?.getString(key, defValue) ?: defValue
    }

    fun getStringSet(key: String, defValue: MutableSet<String>): MutableSet<String> {
        val originalSet = mSP?.getStringSet(key, defValue) ?: defValue
        return HashSet(originalSet)
    }
}