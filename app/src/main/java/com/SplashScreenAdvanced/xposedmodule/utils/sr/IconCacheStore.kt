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

    /** 单条缓存记录 */
    @Serializable
    data class Entry(
        val file: String,
        /** 应用版本号, 与应用更新检测配对使用 */
        val versionCode: Long = 0L,
        /** 应用最后更新时间, 与应用更新检测配对使用 */
        val lastUpdateTime: Long = 0L,
        /** 生成时的目标显示尺寸, 与运行期实际尺寸不符时视为未命中 */
        val targetSize: Int = 0,
        val createdAt: Long = 0L,
    )

    @Serializable
    data class Index(
        val version: Int = 1,
        val entries: Map<String, Entry> = emptyMap(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun indexFile(context: Context): File = File(context.filesDir, INDEX_NAME)

    fun fileFor(context: Context, name: String): File = File(cacheDir(context), name)

    fun readIndex(context: Context): Index = runCatching {
        val f = indexFile(context)
        if (!f.isFile) return@runCatching Index()
        json.decodeFromString(Index.serializer(), f.readText())
    }.getOrDefault(Index())

    private fun writeIndex(context: Context, index: Index): Boolean = runCatching {
        val f = indexFile(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(json.encodeToString(Index.serializer(), index))
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    /**
     * 写入一条缓存（文件 + 索引）
     *
     * 先落文件再更新索引: 反过来的话, 索引更新成功而文件失败会留下"索引指向不存在文件"的
     * 脏状态。当前顺序下最坏情况是"文件已写入但索引未更新", 下次扫描会覆盖它。
     */
    fun put(context: Context, pkg: String, entry: Entry, data: ByteArray): Boolean = runCatching {
        val f = fileFor(context, entry.file)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
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
    }

    fun totalBytes(context: Context): Long =
        cacheDir(context).listFiles()?.sumOf { it.length() } ?: 0L

    /** 生成缓存文件名（不含目录） */
    fun fileNameFor(pkg: String, stamp: Long): String = "$pkg-$stamp.webp"
}
