package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Skill
import com.mozhuan.data.repository.SkillRepository
import kotlinx.coroutines.flow.StateFlow

class SkillViewModel(
    private val skillRepository: SkillRepository,
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = skillRepository.skills

    fun saveSkill(skill: Skill) = skillRepository.saveSkill(skill)

    fun deleteSkill(skillId: String) = skillRepository.deleteSkill(skillId)

    fun setEnabled(skillId: String, enabled: Boolean) = skillRepository.setEnabled(skillId, enabled)

    fun exportSkill(skill: Skill): String = skillRepository.exportSkill(skill)

    fun importSkill(json: String): Result<Skill> = skillRepository.importSkill(json)
}
