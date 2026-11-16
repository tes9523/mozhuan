package com.mozhuan.ui.screen.skills

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Skill
import com.mozhuan.data.repository.SkillRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 技能管理 ViewModel：查看、新增、编辑、删除全局技能。
 * 内置技能不可删除、不可编辑。
 */
class SkillManagementViewModel(
    private val skillRepository: SkillRepository,
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = skillRepository.skills

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun addSkill(name: String, prompt: String, tags: List<String>): Boolean {
        if (name.isBlank() || prompt.isBlank()) {
            _message.value = "技能名称与 Prompt 不能为空"
            return false
        }
        skillRepository.addSkill(name, prompt, tags)
        return true
    }

    fun updateSkill(id: String, name: String, prompt: String, tags: List<String>): Boolean {
        if (name.isBlank() || prompt.isBlank()) {
            _message.value = "技能名称与 Prompt 不能为空"
            return false
        }
        skillRepository.updateSkill(id, name, prompt, tags)
        return true
    }

    fun deleteSkill(id: String) {
        if (!skillRepository.deleteSkill(id)) {
            _message.value = "内置技能不可删除"
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
