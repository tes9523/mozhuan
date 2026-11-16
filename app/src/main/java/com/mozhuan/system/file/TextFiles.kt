package com.mozhuan.system.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** SAF 文本读写，仅处理用户明确选择的文件。 */
object TextFiles {

    fun read(context: Context, uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        } ?: error("无法读取所选文件")

    fun write(context: Context, uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(content)
        } ?: error("无法写入所选文件")
    }

    fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "导入文本.txt"
    }

    fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "mozhuan" }

    fun withoutExtension(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { "导入技能" }
}
