package com.SplashScreenAdvanced.xposedmodule.hook.utils

import com.highcapable.kavaref.extension.makeAccessible
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 进程级反射缓存
 *
 * 宿主类/成员在进程内稳定, 解析一次后复用 [Field]/[Method], 省去每次重复扫描与 resolver 分配
 * 按 (运行时类, 成员名[, 参数个数]) 缓存, 沿父类查找, 异常返回 null
 */
object ReflectCache {
    private data class FieldKey(val clazz: Class<*>, val name: String)
    private data class MethodKey(val clazz: Class<*>, val name: String, val paramCount: Int)

    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()
    private val methodCache = ConcurrentHashMap<MethodKey, Method>()

    // 负缓存: 记录"查过且确实不存在"的成员。
    // 宿主成员在进程内是否存在同样是恒定的, 不缓存 miss 的话每次调用都要沿整条继承链重新查一遍——
    // 字段路径上每层还要构造并捕获一个 NoSuchFieldException (fillInStackTrace 是这里最贵的部分),
    // 而这些调用就落在应用启动的关键路径上 (如 targetActivityInfo 在部分 ROM 上本就不存在)
    private val fieldMisses = ConcurrentHashMap.newKeySet<FieldKey>()
    private val methodMisses = ConcurrentHashMap.newKeySet<MethodKey>()

    /** 解析 [clazz] (含父类) 中名为 [name] 的字段并缓存; 找不到返回 null */
    private fun resolveField(clazz: Class<*>, name: String): Field? {
        val key = FieldKey(clazz, name)
        fieldCache[key]?.let { return it }
        if (key in fieldMisses) return null
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
                    .apply { makeAccessible() }
                    .also { fieldCache[key] = it }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        fieldMisses += key
        return null
    }

    /** 解析 [clazz] (含父类) 中名为 [name]、参数个数为 [paramCount] 的首个方法并缓存; 找不到返回 null */
    private fun resolveMethod(clazz: Class<*>, name: String, paramCount: Int): Method? {
        val key = MethodKey(clazz, name, paramCount)
        methodCache[key]?.let { return it }
        if (key in methodMisses) return null
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == paramCount }?.let {
                it.makeAccessible()
                methodCache[key] = it
                return it
            }
            current = current.superclass
        }
        methodMisses += key
        return null
    }

    /** 读取 [instance] 中名为 [name] 的字段值 */
    @Suppress("UNCHECKED_CAST")
    fun <T> getField(instance: Any, name: String): T? =
        try {
            resolveField(instance.javaClass, name)?.get(instance) as? T
        } catch (_: Throwable) {
            null
        }

    /** 写入 [instance] 中名为 [name] 的字段值 */
    fun setField(instance: Any, name: String, value: Any?) {
        try {
            resolveField(instance.javaClass, name)?.set(instance, value)
        } catch (_: Throwable) {
        }
    }

    /** 调用 [instance] 中名为 [name] 的方法 (按方法名 + 参数个数解析首个匹配) */
    @Suppress("UNCHECKED_CAST")
    fun <T> invokeMethod(instance: Any, name: String, vararg args: Any?): T? =
        try {
            resolveMethod(instance.javaClass, name, args.size)?.invoke(instance, *args) as? T
        } catch (_: Throwable) {
            null
        }
}
