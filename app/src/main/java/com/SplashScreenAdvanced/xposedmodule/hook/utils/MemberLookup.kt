package com.SplashScreenAdvanced.xposedmodule.hook.utils

import io.github.jichuo1.meguviz.ref.api.descriptor.MemberKind
import io.github.jichuo1.meguviz.ref.api.query.Hierarchy
import io.github.jichuo1.meguviz.ref.api.query.QuerySpec
import io.github.jichuo1.meguviz.ref.api.query.Selection
import io.github.jichuo1.meguviz.ref.api.result.ResolutionStatus
import io.github.jichuo1.meguviz.ref.scope.CodeScope
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import kotlin.reflect.KClass

/*
 * 宿主类与成员的查找入口，成员解析统一经 MeguvizRef（CodeScope + QuerySpec）。
 *
 * 查找语义：默认只看声明类自身（Hierarchy.DECLARED），需要沿父类链时显式传 superclasses = true；
 * 找不到、签名无法加载或解析不完整且无命中时一律返回 null，由调用方决定是否降级。
 */

/** 成员筛选条件，写法沿用既有调用点的 DSL */
internal class MemberQuery {
    var name: String? = null

    /** 精确参数个数；-1 表示不限 */
    var parameterCount: Int = -1

    private var countPredicate: ((Int) -> Boolean)? = null
    private var exactParameters: Array<Class<*>>? = null
    private var parameterPredicate: ((List<Class<*>>) -> Boolean)? = null

    fun parameterCount(predicate: (Int) -> Boolean) {
        countPredicate = predicate
    }

    /** 参数类型逐一相等；`Int::class` 等按 Kotlin 约定对应基本类型 */
    fun parameters(vararg types: KClass<*>) {
        exactParameters = Array(types.size) { types[it].java }
    }

    fun parameters(predicate: (List<Class<*>>) -> Boolean) {
        parameterPredicate = predicate
    }

    fun emptyParameters() {
        exactParameters = emptyArray()
    }

    internal fun toSpec(owner: Class<*>, kind: MemberKind, hierarchy: Hierarchy): QuerySpec {
        val builder = QuerySpec.builder(owner, kind).hierarchy(hierarchy)
        name?.let { builder.name(it) }
        val exact = exactParameters
        if (exact != null) builder.parameters(*exact)
        else if (parameterCount >= 0) builder.parameterCount(parameterCount)
        // 谓词条件 MeguvizRef 查不了，先按名称 / 个数取全集，再在这里筛
        builder.selection(if (needsPostFilter) Selection.ALL else Selection.FIRST)
        return builder.build()
    }

    internal val needsPostFilter: Boolean
        get() = countPredicate != null || parameterPredicate != null

    internal fun accepts(member: Member): Boolean {
        val types = when (member) {
            is Method -> member.parameterTypes
            is Constructor<*> -> member.parameterTypes
            else -> return true
        }
        return (countPredicate?.invoke(types.size) ?: true) &&
                (parameterPredicate?.invoke(types.asList()) ?: true)
    }
}

private val acceptedStatuses = setOf(
    ResolutionStatus.RESOLVED,
    ResolutionStatus.ALL_MATCHES,
    // 个别候选签名无法加载时整体标 PARTIAL，但已命中的成员仍然有效
    ResolutionStatus.PARTIAL,
)

private fun Class<*>.firstMember(kind: MemberKind, hierarchy: Hierarchy, query: MemberQuery): Member? =
    try {
        // 短命作用域：成员导出后即关闭，不持有加载器，也不占绑定容量
        CodeScope.open(classLoader ?: ClassLoader.getSystemClassLoader()).use { scope ->
            val result = scope.resolve(query.toSpec(this, kind, hierarchy))
            if (result.status() !in acceptedStatuses) return@use null
            result.members().firstNotNullOfOrNull { token ->
                scope.exportRaw(token).takeIf { query.accepts(it) }
            }
        }
    } catch (_: Throwable) {
        null
    }

private fun hierarchyOf(superclasses: Boolean) = if (superclasses) Hierarchy.SUPERCLASSES else Hierarchy.DECLARED

internal fun Class<*>.findMethod(superclasses: Boolean = false, block: MemberQuery.() -> Unit = {}): Method? =
    firstMember(MemberKind.METHOD, hierarchyOf(superclasses), MemberQuery().apply(block)) as? Method

internal fun Class<*>.findConstructor(block: MemberQuery.() -> Unit = {}): Constructor<*>? =
    firstMember(MemberKind.CONSTRUCTOR, Hierarchy.DECLARED, MemberQuery().apply(block)) as? Constructor<*>

internal fun Class<*>.findField(superclasses: Boolean = false, block: MemberQuery.() -> Unit): Field? =
    firstMember(MemberKind.FIELD, hierarchyOf(superclasses), MemberQuery().apply(block)) as? Field

/** 与 [findField] 相同，但缺失时抛 [NoSuchFieldException]，用于调用方依赖其存在的场景 */
internal fun Class<*>.requireField(superclasses: Boolean = false, block: MemberQuery.() -> Unit): Field =
    findField(superclasses, block) ?: throw NoSuchFieldException("$name: field not found")

/** 按名称加载类，不触发初始化；[loader] 缺省时使用系统类加载器 */
internal fun String.toClass(loader: ClassLoader? = null): Class<*> =
    Class.forName(this, false, loader ?: ClassLoader.getSystemClassLoader())

internal fun String.toClassOrNull(loader: ClassLoader? = null): Class<*>? =
    try {
        toClass(loader)
    } catch (_: Throwable) {
        null
    }

/**
 * `T::class.java`；基本类型默认取原始类型（`classOf<Int>()` 是 `int`、`classOf<Boolean>()` 是 `boolean`）。
 *
 * 这是 KavaRef `classOf(primitiveType = true)` 的默认语义，不能改：`getDeclaredMethod` 按签名精确匹配，
 * 传入 `Integer` / `Boolean` 去找 `(int)` / `(boolean)` 参数的方法会 NoSuchMethodException，而调用点都包在
 * `runCatching` 里，表现为功能静默失效（1.0.8 曾因此无法去除小米图标描边，也丢了 AppIconsManager 一层）。
 * 确实需要包装类时传 `primitive = false`。
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> classOf(primitive: Boolean = true): Class<T> =
    ((if (primitive) T::class.javaPrimitiveType else null) ?: T::class.java) as Class<T>

/** 放宽访问检查；平台拒绝时静默保留原状，由随后的访问自行失败 */
internal fun <T : AccessibleObject> T.makeAccessible(): T = apply {
    try {
        isAccessible = true
    } catch (_: Throwable) {
    }
}

/**
 * 类型化访问辅助
 */
internal class TypedField<T : Any, V>(private val rawField: Field) {
    init {
        rawField.makeAccessible()
    }

    @Suppress("UNCHECKED_CAST")
    fun get(instance: T?): V? = try {
        rawField.get(instance) as? V
    } catch (_: Throwable) {
        null
    }

    fun set(instance: T?, value: V?) {
        try {
            rawField.set(instance, value)
        } catch (_: Throwable) {
        }
    }
}

internal class TypedMethod<T : Any, R>(private val rawMethod: Method) {
    init {
        rawMethod.makeAccessible()
    }

    @Suppress("UNCHECKED_CAST")
    fun invoke(instance: T?, vararg args: Any?): R? = try {
        rawMethod.invoke(instance, *args) as? R
    } catch (_: Throwable) {
        null
    }
}

internal class TypedConstructor<T : Any>(private val rawConstructor: Constructor<*>) {
    init {
        rawConstructor.makeAccessible()
    }

    @Suppress("UNCHECKED_CAST")
    fun newInstance(vararg args: Any?): T? = try {
        rawConstructor.newInstance(*args) as? T
    } catch (_: Throwable) {
        null
    }
}

internal inline fun <reified V> Field.toTyped(): TypedField<Any, V> = TypedField(this)
internal inline fun <reified R> Method.toTyped(): TypedMethod<Any, R> = TypedMethod(this)

internal inline fun <T : Any, reified V> Field.getValueFrom(instance: T?): V? =
    makeAccessible().get(instance) as? V

internal fun <T : Any> Field.setValueTo(instance: T?, value: Any?) {
    makeAccessible().set(instance, value)
}
