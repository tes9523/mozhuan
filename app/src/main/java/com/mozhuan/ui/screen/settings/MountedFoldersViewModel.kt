package com.mozhuan.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.MountedFolder
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.system.file.TextFiles
import kotlinx.coroutines.flow.StateFlow

/** 挂载外部文件夹：通过 SAF 目录授权持久保存，支持浏览与读取文本文件。 */
class MountedFoldersViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val folders: StateFlow<List<MountedFolder>> = settingsRepository.mountedFolders

    fun add(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = DocumentFile.fromTreeUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "外部文件夹"
        settingsRepository.addMountedFolder(MountedFolder(name = name, uri = uri.toString()))
    }

    fun remove(folder: MountedFolder) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsRepository.removeMountedFolder(folder.id)
    }

    fun children(folder: MountedFolder): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return emptyList()
        return childrenOf(root)
    }

    fun childrenOf(directory: DocumentFile): List<DocumentFile> =
        directory.listFiles().sortedWith(
            compareBy({ !it.isDirectory }, { it.name.orEmpty().lowercase() }),
        )

    fun readText(file: DocumentFile): Result<String> = runCatching {
        TextFiles.read(context, file.uri)
    }
}
