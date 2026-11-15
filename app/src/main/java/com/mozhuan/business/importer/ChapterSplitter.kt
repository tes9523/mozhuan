package com.mozhuan.business.importer

data class SplitChapter(
    val title: String,
    val content: String,
)

/**
 * 章节识别：
 * - TXT：识别“第X章/节/卷/回/篇”“序章/楔子/番外”“Chapter N”等行；
 * - Markdown：识别 1~3 级标题；
 * - 标题之前的内容归入“开篇”，无任何标题时整篇作为一章。
 */
object ChapterSplitter {

    private val txtChapterRegex = Regex(
        """^\s*(第[0-9零一二三四五六七八九十百千万两]+[章节卷回篇部集](?:\s*[:：.、\-—]?\s*.*)?|(?:序章|楔子|引子|前言|后记|尾声|终章|番外篇?)(?:\s*[:：.、\-—]?\s*.*)?|(?:Chapter|CHAPTER)\s+\d+(?:\s*[:：.]?\s*.*)?)\s*$""",
    )

    private val markdownHeadingRegex = Regex("""^(#{1,3})\s+(.+?)\s*#*\s*$""")

    fun split(rawText: String, isMarkdown: Boolean, fallbackTitle: String = "开篇"): List<SplitChapter> {
        val text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isBlank()) return emptyList()

        val headingRegex = if (isMarkdown) markdownHeadingRegex else txtChapterRegex
        val result = mutableListOf<SplitChapter>()
        val buffer = mutableListOf<String>()
        var currentTitle: String? = null

        fun flush() {
            val content = buffer.joinToString("\n").trim()
            buffer.clear()
            val title = currentTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle
            if (currentTitle != null || content.isNotBlank()) {
                result += SplitChapter(title = title, content = content)
            }
            currentTitle = null
        }

        text.lineSequence().forEach { line ->
            val match = headingRegex.find(line)
            if (match != null) {
                flush()
                currentTitle = if (isMarkdown) {
                    match.groupValues[2].trim().take(60)
                } else {
                    line.trim().take(60)
                }
            } else {
                buffer += line
            }
        }
        flush()

        return result.map { chapter ->
            chapter.copy(title = chapter.title.ifBlank { fallbackTitle })
        }
    }
}
