package com.mozhuan.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Skill
import com.mozhuan.data.repository.SettingsRepository
import com.mozhuan.data.repository.SkillRepository
import kotlinx.coroutines.flow.StateFlow

class SharedFolderViewModel(
    private val settingsRepository: SettingsRepository,
    private val skillRepository: SkillRepository,
) : ViewModel() {

    val skills: StateFlow<List<Skill>> = skillRepository.skills
    val persona: StateFlow<String> = settingsRepository.persona

    fun importSkill(name: String, content: String): Skill =
        skillRepository.importSkill(content, name).getOrThrow()

    fun exportSkill(skill: Skill): String = skillRepository.exportMarkdown(skill)

    fun refreshSkills() = skillRepository.reload()

    fun importPersona(content: String) = settingsRepository.setPersona(content)
}
