package com.mozhuan.business.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    @Test
    fun `txt text is split by chinese chapter headings`() {
        val text = """
            这是前言内容。
            第一章 初见
            正文一。
            第二章 再会
            正文二。
        """.trimIndent()

        val chapters = ChapterSplitter.split(text, isMarkdown = false, fallbackTitle = "开篇")

        assertEquals(3, chapters.size)
        assertEquals("开篇", chapters[0].title)
        assertEquals("第一章 初见", chapters[1].title)
        assertEquals("正文一。", chapters[1].content)
        assertEquals("正文二。", chapters[2].content)
    }

    @Test
    fun `markdown text is split by headings`() {
        val text = """
            # 第一章
            内容 A
            ## 第一节
            内容 B
        """.trimIndent()

        val chapters = ChapterSplitter.split(text, isMarkdown = true)

        assertEquals(2, chapters.size)
        assertEquals("第一章", chapters[0].title)
        assertEquals("内容 A", chapters[0].content)
        assertEquals("第一节", chapters[1].title)
        assertEquals("内容 B", chapters[1].content)
    }

    @Test
    fun `english chapter headings are recognized`() {
        val text = "Chapter 1 Beginning\nHello\nChapter 2 Ending\nBye"

        val chapters = ChapterSplitter.split(text, isMarkdown = false)

        assertEquals(2, chapters.size)
        assertEquals("Chapter 1 Beginning", chapters[0].title)
        assertEquals("Bye", chapters[1].content)
    }

    @Test
    fun `text without headings becomes a single chapter`() {
        val text = "只有一段普通内容。"

        val chapters = ChapterSplitter.split(text, isMarkdown = false, fallbackTitle = "正文")

        assertEquals(1, chapters.size)
        assertEquals("正文", chapters[0].title)
        assertEquals(text, chapters[0].content)
    }

    @Test
    fun `blank text yields no chapters`() {
        assertTrue(ChapterSplitter.split("   \n\n", isMarkdown = false).isEmpty())
    }
}
