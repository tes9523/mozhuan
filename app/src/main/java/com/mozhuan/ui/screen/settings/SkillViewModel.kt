package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Skill
import com.mozhuan.data.model.SkillFile
import com.mozhuan.data.repository.SkillRepository
import kotlinx.coroutines.flow.StateFlow

/** 技能管理：扫描 SKILL.md、开关、编辑、导入导出。 */
class SkillViewModel(
    private val repository: SkillRepository,
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = repository.skills

    fun refresh() = repository.reload()

    fun setEnabled(skillId: String, enabled: Boolean) =
        repository.setEnabled(skillId, enabled)

    fun saveSkill(
        id: String?,
        name: String,
        description: String,
        body: String,
        enabled: Boolean,
    ) {
        repository.saveSkill(id, name, description, body, enabled)
    }

    fun deleteSkill(skillId: String) = repository.deleteSkill(skillId)

    fun importSkill(content: String, fallbackName: String?): Result<Skill> =
        repository.importSkill(content, fallbackName)

    fun importArchive(input: java.io.InputStream): Result<Skill> =
        repository.importArchive(input)

    fun exportSkill(skill: Skill): String = repository.exportMarkdown(skill)

    fun exportArchive(skill: Skill): ByteArray? = repository.exportArchive(skill)

    fun attachments(skillId: String): List<SkillFile> = repository.listAttachments(skillId)

    fun readAttachment(skillId: String, path: String): String? =
        repository.readAttachment(skillId, path)

    fun skillPath(skillId: String): String = repository.skillMdPath(skillId)
}
