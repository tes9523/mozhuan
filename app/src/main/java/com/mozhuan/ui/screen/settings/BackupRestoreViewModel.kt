package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.repository.BackupRepository

/** 备份与恢复：导出 / 导入单个 JSON 备份文件。 */
class BackupRestoreViewModel(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    fun exportJson(): String = backupRepository.exportJson()

    fun importJson(content: String): Result<String> = backupRepository.importJson(content)
}
