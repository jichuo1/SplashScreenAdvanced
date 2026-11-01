package com.SplashScreenAdvanced.xposedmodule.hook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

open class LookupBase {
    @Suppress("unused")
    private val baseField = 7

    @Suppress("unused")
    fun inherited(): Int = 0
}

@Suppress("unused", "UNUSED_PARAMETER")
class LookupTarget() : LookupBase() {
    private val secret = "hidden"

    constructor(a: Int, b: String) : this()

    fun alpha(): Int = 1
    fun alpha(x: Int): Int = x
    fun alpha(a: String, b: String): String = a + b
    private fun beta(a: String, b: Boolean, c: Float): String = a
    fun gamma(a: Int, b: Int, c: Int): Int = a
}

class MemberLookupTest {
    private val target = LookupTarget::class.java

    @Test
    fun methodByNameOnly() {
        assertEquals("alpha", target.findMethod { name = "alpha" }?.name)
    }

    @Test
    fun exactParameterCountSelectsTheOverload() {
        assertEquals(1, target.findMethod { name = "alpha"; parameterCount = 1 }?.parameterCount)
        assertEquals(2, target.findMethod { name = "alpha"; parameterCount = 2 }?.parameterCount)
    }

    @Test
    fun exactParametersMapKotlinPrimitives() {
        val single = target.findMethod { name = "alpha"; parameters(Int::class) }
        assertEquals(listOf(Int::class.javaPrimitiveType), single?.parameterTypes?.toList())

        val privateOne = target.findMethod { name = "beta"; parameters(String::class, Boolean::class, Float::class) }
        assertNotNull(privateOne)
        assertEquals(String::class.java, privateOne?.returnType)
    }

    @Test
    fun emptyParametersPicksTheNoArgOverload() {
        assertEquals(0, target.findMethod { name = "alpha"; emptyParameters() }?.parameterCount)
    }

    @Test
    fun countPredicateFiltersAfterTheNameQuery() {
        val method = target.findMethod { name = "alpha"; parameterCount { it >= 2 } }
        assertEquals(2, method?.parameterCount)
        assertNull(target.findMethod { name = "alpha"; parameterCount { it > 5 } })
    }

    @Test
    fun parameterPredicateSeesTheDeclaredTypes() {
        val method = target.findMethod {
            name = "alpha"
            parameters { types -> types.size == 2 && types.all { it == String::class.java } }
        }
        assertEquals(2, method?.parameterCount)
        assertNull(target.findMethod { name = "alpha"; parameters { types -> java.lang.Long.TYPE in types } })
    }

    @Test
    fun missingMemberIsNull() {
        assertNull(target.findMethod { name = "doesNotExist" })
        assertNull(target.findField { name = "doesNotExist" })
    }

    @Test
    fun declaredOnlyByDefaultAndSuperclassesOnRequest() {
        assertNull(target.findMethod { name = "inherited" })
        assertEquals(LookupBase::class.java, target.findMethod(superclasses = true) { name = "inherited" }?.declaringClass)
        assertNull(target.findField { name = "baseField" })
        assertEquals(LookupBase::class.java, target.findField(superclasses = true) { name = "baseField" }?.declaringClass)
    }

    @Test
    fun constructorsWithAndWithoutFilters() {
        assertNotNull(target.findConstructor())
        assertEquals(2, target.findConstructor { parameterCount = 2 }?.parameterCount)
        assertEquals(2, target.findConstructor { parameterCount { it >= 2 } }?.parameterCount)
        assertNull(target.findConstructor { parameterCount = 5 })
    }

    @Test
    fun requireFieldThrowsWhenMissing() {
        assertEquals("secret", target.requireField { name = "secret" }.name)
        assertThrows(NoSuchFieldException::class.java) { target.requireField { name = "nope" } }
    }

    @Test
    fun privateFieldIsReadableAfterMakeAccessible() {
        val field = target.requireField { name = "secret" }
        assertSame(field, field.makeAccessible())
        assertEquals("hidden", field.getValueFrom<Any, String>(LookupTarget()))
    }

    @Test
    fun classLoadingHelpers() {
        assertEquals(String::class.java, "java.lang.String".toClass())
        assertNull("no.such.Clazz".toClassOrNull())
        // 与此前 KavaRef 的 classOf 一致: reified 基本类型取包装类
        assertEquals(Int::class.javaObjectType, classOf<Int>())
        assertEquals(String::class.java, classOf<String>())
    }
}
