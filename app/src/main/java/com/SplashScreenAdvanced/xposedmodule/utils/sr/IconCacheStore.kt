package com.SplashScreenAdvanced.xposedmodule.utils.sr

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 离线超分缓存的落盘层
 *
 * 结构:
 * ```
 * filesDir/
 *   ├ sr_index.json          索引（小、可整份读写）
 *   └ sr_cache/
 *       └ <pkg>-<hash>.webp  单个应用的增强结果（WebP **无损**）
 * ```
 *
 * 约定:
 * - 一律**先写 .tmp 再 rename**, 避免 SystemUI 侧读到半成品;
 * - 用 WebP 无损而非有损: 有损会引入新的压缩伪影, 与"提升画质"的目标相悖;
 * - 索引整体读写（条目数量级在百, 无需增量更新）。
 */
internal object IconCacheStore {

    private const val DIR_NAME = "sr_cache"
    private const val INDEX_NAME = "sr_index.json"

    /** 某个尺寸下的缓存文件 */
    @Serializable
    data class Variant(val size: Int, val file: String)

    /** 单条缓存记录 */
    @Serializable
    data class Entry(
        /** 主文件（等价于 [variants] 的第一项, 保留是为了兼容旧索引） */
        val file: String,
        /** 应用版本号, 与应用更新检测配对使用 */
        val versionCode: Long = 0L,
        /** 应用最后更新时间, 与应用更新检测配对使用 */
        val lastUpdateTime: Long = 0L,
        /** [file] 对应的尺寸 */
        val targetSize: Int = 0,
        val createdAt: Long = 0L,
        /**
         * 多尺寸变体
         *
         * 宿主可能按两种尺寸绘制同一个图标: `starting_surface_icon_size`(基准, 如 640px), 或
         * 基准 x `splash_icon_no_background_scale_factor`(AOSP 为 192/160 = 1.2, 即 768px)。
         * 走哪支取决于图标的"前景非透明比例", 该判定只在运行期做, 离线无法预知, 所以两份都产出,
         * 保证无论走哪支都能精确命中。
         *
         * 旧版本写入的缓存没有这个字段, [fileFor] 会自动回退到 [file] + [targetSize]。
         */
        val variants: List<Variant> = emptyList(),
    ) {
        /** 这条记录涉及的全部文件名（供清理旧变体使用） */
        fun allFiles(): List<String> = (variants.map { it.file } + file).distinct()

        /**
         * 按请求尺寸挑选文件名
         *
         * 精确命中优先; 退而求其次取**比请求更大**的变体中最小的一份 —— 缩小绘制比放大清晰。
         * 两者都不满足时返回 `null`, 调用方按未命中处理。
         */
        fun fileFor(requestSize: Int): String? {
            variants.firstOrNull { it.size == requestSize }?.let { return it.file }
            variants.filter { it.size > requestSize }.minByOrNull { it.size }?.let { return it.file }
            return if (targetSize == requestSize) file else null
        }
    }

    @Serializable
    data class Index(
        val version: Int = 1,
        val entries: Map<String, Entry> = emptyMap(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val indexLock = Any()

    @Volatile
    private var indexCache: Index? = null

    private fun cacheDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_NAME)

    fun fileFor(context: Context, name: String): File = File(cacheDir(context), name)

    /** 缓存目录下的全部文件（供扫描服务清理孤儿文件使用） */
    fun cacheFiles(context: Context): List<File> =
        cacheDir(context).listFiles()?.toList() ?: emptyList()

    /**
     * 读取索引
     *
     * 进程内缓存是必要的: `IconCacheProvider.openFile` 每次跨进程读取都会走到这里, 而索引有
     * 上百条、几十 KB —— 反复"读文件 + JSON 解析"会直接压在 SystemUI 的启动路径上。
     *
     * 失效策略: [writeIndex] 成功时同步替换缓存, 因此模块进程内不会读到过期数据。SystemUI
     * 侧不读索引(只拿 FD), 不需要跨进程失效。
     */
    fun readIndex(context: Context): Index {
        indexCache?.let { return it }
        return synchronized(indexLock) {
            indexCache?.let { return it }
            val loaded = runCatching {
                val f = indexFile(context)
                if (!f.isFile) Index() else json.decodeFromString(Index.serializer(), f.readText())
            }.getOrDefault(Index())
            indexCache = loaded
            loaded
        }
    }

    private fun writeIndex(context: Context, index: Index): Boolean {
        val ok = runCatching {
            val f = indexFile(context)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(json.encodeToString(Index.serializer(), index))
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrDefault(false)
        // 只在落盘成功后替换缓存, 否则内存与磁盘会不一致
        if (ok) synchronized(indexLock) { indexCache = index }
        return ok
    }

    /**
     * 只落一个缓存文件, 不触碰索引
     *
     * 供需要写多份尺寸变体的调用方使用: 全部文件写成功后再用 [putEntry] 一次性让索引指向它们,
     * 避免中途失败留下"索引指向半套变体"的状态。
     */
    fun putFile(context: Context, name: String, data: ByteArray): Boolean = runCatching {
        val f = fileFor(context, name)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    /**
     * 只更新索引条目
     *
     * 供已经用 [putFile] 落好全部变体文件的调用方使用: 文件先全部写成功, 再用本方法一次性
     * 让索引指向它们, 避免中途失败留下"索引指向半套变体"的状态。
     */
    fun putEntry(context: Context, pkg: String, entry: Entry): Boolean = runCatching {
        val index = readIndex(context)
        writeIndex(context, index.copy(entries = index.entries + (pkg to entry)))
    }.getOrDefault(false)

    fun remove(context: Context, pkg: String) {
        runCatching {
            val index = readIndex(context)
            index.entries[pkg]?.let { fileFor(context, it.file).delete() }
            writeIndex(context, index.copy(entries = index.entries - pkg))
        }
    }

    fun clear(context: Context) {
        runCatching {
            cacheDir(context).listFiles()?.forEach { it.delete() }
            indexFile(context).delete()
        }
        synchronized(indexLock) { indexCache = Index() }
    }

    fun totalBytes(context: Context): Long =
        cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** 生成缓存文件名（不含目录）。带尺寸便于人工排查, 不参与任何逻辑判定 */
    fun fileNameFor(pkg: String, size: Int, stamp: Long): String = "$pkg-$size-$stamp.webp"
}
