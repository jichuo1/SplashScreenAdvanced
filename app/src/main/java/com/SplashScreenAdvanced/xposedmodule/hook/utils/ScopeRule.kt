package com.SplashScreenAdvanced.xposedmodule.hook.utils

/** 自定义作用域判定的纯函数部分，偏好读取留在调用方 */
internal object ScopeRule {
    /**
     * 该应用是否在模块作用域之外
     *
     * 「强制显示遮罩」列表里的应用是用户明确要求由模块弹出遮罩的，若再被「仅选中」挡在作用域外，
     * 遮罩会被强制弹出却不经过任何图标 / 圆角 / 背景处理。因此「仅选中」模式下它们视为作用域内；
     * 「排除」模式下用户明确排除了该应用，排除优先。
     *
     * @param forceShowPackages 强制显示列表；开关关闭时调用方传空集
     */
    fun isExcept(
        packageName: String,
        customScopeEnabled: Boolean,
        exceptionMode: Boolean,
        scopeList: Set<String>,
        forceShowPackages: Set<String>,
    ): Boolean = when {
        packageName.isBlank() -> true
        !customScopeEnabled -> false
        exceptionMode -> packageName in scopeList
        else -> packageName !in scopeList && packageName !in forceShowPackages
    }
}
