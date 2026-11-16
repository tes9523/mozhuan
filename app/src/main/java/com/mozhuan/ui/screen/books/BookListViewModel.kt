package com.mozhuan.ui.screen.books

import androidx.lifecycle.ViewModel
import com.mozhuan.data.model.Book
import com.mozhuan.data.repository.BookRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * 书稿列表 ViewModel。
 */
class BookListViewModel(
    private val bookRepository: BookRepository,
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookRepository.books

    /** 新建书稿并返回 ID，供页面跳转详情。 */
    fun createBook(title: String): String {
        return bookRepository.createBook(title).id
    }
}
