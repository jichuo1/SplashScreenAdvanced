package com.SplashScreenAdvanced.xposedmodule.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * 图标包处理类
 *
 * @param mContext 上下文 - 必填
 * @param packageName 图标包包名 - 如不需获取图标, 可不填
 */
class IconPackManager(private val mContext: Context, private val packageName: String? = null) {

    /**
     * "已尝试过加载" 标记 (volatile: 是 [mPackagesDrawables]/[iconPackRes] 的发布屏障,
     * 读到 true 的线程保证能看到 load() 内完成的全部写入)
     *
     * 无论成败都在 [load] 末尾置位: 图标包被卸载时 getResourcesForApplication 抛
     * NameNotFoundException, 若失败不置位, 每次应用启动都会在关键路径上重跑 binder 查询
     */
    @Volatile
    private var mLoaded = false
    private val mPackagesDrawables = HashMap<String?, String?>()
    private var iconPackRes: Resources? = null

    @SuppressLint("DiscouragedApi")
    private fun load() {
        // 后台预热线程与启动遮罩路径可能并发进入, 必须互斥:
        // HashMap 写时不允许并发读 (扩容期间并发读可能死循环/丢条目)
        synchronized(this) {
            if (mLoaded) return

            // load appfilter.xml from the icon pack package
            val pm = mContext.packageManager
            try {
                // 解析完必须释放: XmlResourceParser 与 assets 流都持有原生资源,
                // 而本类在常驻的 SystemUI 进程里使用, 漏掉就是一直挂着
                var parser: XmlResourceParser? = null
                var appFilterStream: InputStream? = null
                try {
                    val xpp: XmlPullParser?
                    val res = pm.getResourcesForApplication(packageName!!)
                    iconPackRes = res
                    val appFilterID = res.getIdentifier("appfilter", "xml", packageName)
                    if (appFilterID > 0) {
                        parser = res.getXml(appFilterID)
                        xpp = parser
                    } else {
                        // no resource found, try to open it from assests folder
                        xpp = try {
                            appFilterStream = res.assets.open("appfilter.xml")
                            val factory = XmlPullParserFactory.newInstance()
                            factory.isNamespaceAware = true
                            factory.newPullParser().apply { setInput(appFilterStream, "utf-8") }
                        } catch (_: IOException) {
                            //XMLog.d { "No appfilter.xml file" }
                            null
                        }
                    }
                    if (xpp != null) {
                        var eventType = xpp.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if (xpp.name == "item") {
                                    var componentName: String? = null
                                    var drawableName: String? = null
                                    for (i in 0 until xpp.attributeCount) {
                                        if (xpp.getAttributeName(i) == "component") {
                                            componentName = xpp.getAttributeValue(i)
                                        } else if (xpp.getAttributeName(i) == "drawable") {
                                            drawableName = xpp.getAttributeValue(i)
                                        }
                                    }
                                    if (!mPackagesDrawables.containsKey(componentName)) {
                                        mPackagesDrawables[componentName] = drawableName
                                    }
                                }
                            }
                            eventType = xpp.next()
                        }
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    //XMLog.d { "Cannot load icon pack" }
                } catch (_: XmlPullParserException) {
                    //XMLog.d { "Cannot parse icon pack appfilter.xml" }
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    runCatching { parser?.close() }
                    runCatching { appFilterStream?.close() }
                }
            } finally {
                // 无论成败都置位(见字段注释); 置位在所有写入之后, 保证 volatile 发布语义
                mLoaded = true
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun loadDrawable(drawableName: String): Drawable? {
        val res = iconPackRes ?: return null
        val id = res.getIdentifier(drawableName, "drawable", packageName)
        if (id > 0) {
            return ResourcesCompat.getDrawable(res, id, mContext.theme)
        }
        return null
    }

    /**
     * 根据应用名获取图标
     *
     * @param appPackageName 需要获取图标的应用包名
     * @return [Drawable]
     */
    @SuppressLint("DiscouragedApi")
    fun getIconByPackageName(appPackageName: String?): Drawable? {
        if (!mLoaded) load()
        val res = iconPackRes ?: return null
        if (appPackageName == null) return null

        val pm = mContext.packageManager
        // getLaunchIntentForPackage 是 binder 调用, 原先连着调了两次, 这里只取一次
        val componentName = pm.getLaunchIntentForPackage(appPackageName)?.component?.toString()

        var drawableName = mPackagesDrawables[componentName]
        if (drawableName != null) {
            return loadDrawable(drawableName)
        } else {
            // try to get a resource with the component filename
            if (componentName != null) {
                val start = componentName.indexOf("{") + 1
                val end = componentName.indexOf("}", start)
                if (end > start) {
                    drawableName =
                        componentName.substring(start, end).lowercase(Locale.getDefault())
                            .replace(".", "_").replace("/", "_")
                    if (res.getIdentifier(drawableName, "drawable", packageName) > 0)
                        return loadDrawable(drawableName)
                }
            }
        }
        return null
    }

    /**
     * 根据应用名获取图标
     *
     * @param componentName 需要获取图标的组件名
     * @return [Drawable]
     */
    fun getIconByComponentName(componentName: String?): Drawable? {
        if (!mLoaded) load()
        val drawableName = mPackagesDrawables[componentName]
        return if (drawableName != null) loadDrawable(drawableName) else null
    }

    /**
     * 获取可用的图标包列表
     *
     * @return [Map] key: 图标包包名; value: 图标包应用名. 默认添加一个键值均为 None 的键值对
     */
    fun getAvailableIconPacks(): Map<String, String> {
        val iconPacks = mutableMapOf("None" to "None")
        val pm = mContext.packageManager

        val rInfo: List<ResolveInfo> = ICON_PACK_ACTIONS.flatMap { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
        }

        for (ri in rInfo) {
            val packageName = ri.activityInfo.packageName
            if (iconPacks.containsKey(packageName)) continue   // 去重
            try {
                val ai = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val appName = pm.getApplicationLabel(ai).toString()
                iconPacks += packageName to appName
            } catch (e: PackageManager.NameNotFoundException) {
                // shouldn't happen
                e.printStackTrace()
            }
        }
        return iconPacks
    }

    companion object {
        /** 图标包 action 兜底 */
        private val ICON_PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.anddoes.launcher.THEME",
            "com.dlto.atom.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "com.zeroteam.zerolauncher.theme",
            "com.fede.launcher.THEME_ICONPACK",
            "com.gtp.nextlauncher.theme",
            "com.gridappsinc.launcher.theme.apk_action",
            "com.teslacoilsw.launcher.THEME",
            "com.novalauncher.THEME",
            "ch.deletescape.lawnchair.ICONPACK",
            "com.lge.launcher2.THEME",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON",
            "com.spocky.projengmenu.icons.ACTION_PICK_ICON",
            "ginlemon.smartlauncher.THEMES",
            "home.solo.launcher.free.THEMES",
            "home.solo.launcher.free.ACTION_ICON",
            "com.sonymobile.home.ICON_PACK",
            "com.tsf.shell.themes",
            "com.phonemetra.turbo.launcher.THEMES",
            "com.phonemetra.turbo.launcher.icons.ACTION_PICK_ICON",
            "mobi.bbase.ahome.THEME",
            "com.rogro.GDE.THEME.1",
            "com.android.dxtop.launcher.THEME",
            "cdproductions.crazyicons.TWO"
        )
    }
}