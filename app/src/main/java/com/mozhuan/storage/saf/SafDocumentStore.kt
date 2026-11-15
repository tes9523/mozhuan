package com.mozhuan.storage.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.mozhuan.storage.preferences.AppPreferences
import java.io.OutputStream
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 SAF 的书稿文件读写。
 *
 * 目录结构：用户选择的根目录 / 书名_书稿ID短码 / 001_章节名.md
 * 注意：子目录与文件必须使用 fromSingleUri 访问，避免误操作根目录。
 */
class SafDocumentStore(
    private val context: Context,
    private val preferences: AppPreferences,
) {

    private val resolver: ContentResolver
        get() = context.contentResolver

    fun rootUri(): Uri? = preferences.rootUri.value?.let { runCatching { Uri.parse(it) }.getOrNull() }

    /** 判断根目录的持久化读写权限是否仍然有效。 */
    fun hasRootPermission(): Boolean {
        val uri = rootUri() ?: return false
        return resolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
    }

    fun rootDisplayName(): String? = runCatching {
        rootUri()?.let { DocumentFile.fromTreeUri(context, it)?.name }
    }.getOrNull()

    /** 授权并校验根目录可写，成功后持久化保存。 */
    suspend fun setRoot(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(DocumentsContract.isTreeUri(uri)) { "请选择一个文件夹" }
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val root = DocumentFile.fromTreeUri(context, uri) ?: error("无法打开所选文件夹")
            val probe = root.createDirectory("MozhuanProbe") ?: error("该文件夹不可写，请重新选择")
            probe.delete()
            preferences.setRootUri(uri.toString())
        }
    }

    /** 在根目录下创建书稿专属文件夹，返回其 document URI。 */
    suspend fun createBookFolder(bookId: String, baseName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = DocumentFile.fromTreeUri(context, requireRoot()) ?: error("无法打开存储目录")
                val folderName = "${baseName.take(30)}_${bookId.take(6)}"
                val dir = root.findFile(folderName)?.takeIf { it.isDirectory }
                    ?: root.createDirectory(folderName)
                    ?: error("创建书稿目录失败")
                dir.uri.toString()
            }
        }

    /** 写入章节 Markdown 文件，返回文件 URI。文件不存在时自动创建。 */
    suspend fun writeChapterFile(
        folderUriString: String,
        fileName: String,
        content: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = DocumentFile.fromSingleUri(context, Uri.parse(folderUriString))
                ?: error("书稿目录不可用")
            check(folder.isDirectory) { "书稿目录已被移动或删除" }
            val file = folder.findFile(fileName)
                ?: folder.createFile(MIME_MARKDOWN, fileName)
                ?: error("创建章节文件失败")
            writeBytes(file.uri, content.toByteArray(Charsets.UTF_8))
            file.uri.toString()
        }
    }

    suspend fun readTextFile(uriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取章节文件")
            TextDecoder.decode(bytes)
        }
    }

    /** 覆盖已存在的章节文件正文。 */
    suspend fun overwriteText(uriString: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                writeBytes(Uri.parse(uriString), content.toByteArray(Charsets.UTF_8))
            }
        }

    /** 读取用户导入的 TXT / Markdown 文件。 */
    suspend fun readImportedText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("无法读取所选文件")
            TextDecoder.decode(bytes)
        }
    }

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    suspend fun writeToUri(target: Uri, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                writeBytes(target, content.toByteArray(Charsets.UTF_8))
            }
        }

    suspend fun deleteDocumentOrFolder(uriString: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(uriString))
                if (doc != null && doc.exists()) {
                    check(doc.delete()) { "删除存储文件失败" }
                }
            }
        }

    private fun requireRoot(): Uri = rootUri() ?: error("尚未选择存储目录")

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val stream: OutputStream = runCatching {
            resolver.openOutputStream(uri, "wt")
        }.getOrNull() ?: resolver.openOutputStream(uri, "w") ?: error("无法写入文件")
        stream.use {
            it.write(bytes)
            it.flush()
        }
    }

    companion object {
        private const val MIME_MARKDOWN = "text/markdown"
    }
}

/** 兼容常见 TXT 编码：UTF-8（含 BOM）、UTF-16 与中文 GB18030 回退。 */
internal object TextDecoder {

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val text = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)

            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)

            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)

            else -> decodeWithoutBom(bytes)
        }
        return text.removePrefix("\uFEFF")
    }

    private fun decodeWithoutBom(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        val brokenCount = utf8.count { it == '\uFFFD' }
        if (brokenCount > 0 && brokenCount * 100 >= utf8.length) {
            return runCatching { String(bytes, Charset.forName("GB18030")) }.getOrDefault(utf8)
        }
        return utf8
    }
}
