package com.mozhuan.ui.screen.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mozhuan.data.model.Book
import com.mozhuan.data.model.Skill
import com.mozhuan.data.repository.BookRepository
import com.mozhuan.data.repository.SkillRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 书稿详情 ViewModel：本书设定、绑定技能、章节列表。
 */
class BookDetailViewModel(
    private val bookId: String,
    private val bookRepository: BookRepository,
    skillRepository: SkillRepository,
) : ViewModel() {

    val book: StateFlow<Book?> = bookRepository.books
        .map { list -> list.firstOrNull { it.id == bookId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = bookRepository.getBook(bookId),
        )

    val skills: StateFlow<List<Skill>> = skillRepository.skills

    fun updateWorldview(text: String) = bookRepository.updateWorldview(bookId, text)

    fun updateCharacters(text: String) = bookRepository.updateCharacters(bookId, text)

    /** 绑定 / 解绑本书专属技能。 */
    fun toggleBoundSkill(skillId: String) {
        val current = bookRepository.getBook(bookId)?.boundSkillIds.orEmpty()
        val next = if (skillId in current) current - skillId else current + skillId
        bookRepository.setBoundSkills(bookId, next)
    }

    /** 新增章节，返回章节 ID 供页面跳转。 */
    fun addChapter(title: String): String? {
        if (title.isBlank()) return null
        return bookRepository.addChapter(bookId, title).id
    }
}
