package com.mozhuan.business.exporter

/** 将 Markdown 粗略转换为纯文本，用于 TXT 导出。 */
object PlainTextConverter {

    private val imageRegex = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val linkRegex = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val boldRegex = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
    private val strikeRegex = Regex("""~~(.+?)~~""")
    private val italicRegex = Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)""")
    private val inlineCodeRegex = Regex("""`([^`]+)`""")
    private val headingRegex = Regex("""^#{1,6}\s+""")
    private val quoteRegex = Regex("""^>\s?""")
    private val listRegex = Regex("""^\s*([-*+]|\d+[.)])\s+""")

    fun convert(markdown: String): String {
        val output = StringBuilder()
        markdown.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("```")) return@forEach
            var text = line.replace(headingRegex, "")
            text = text.replace(quoteRegex, "")
            text = text.replace(listRegex, "")
            text = imageRegex.replace(text, "$1")
            text = linkRegex.replace(text, "$1")
            text = boldRegex.replace(text) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            text = strikeRegex.replace(text, "$1")
            text = italicRegex.replace(text) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
            text = inlineCodeRegex.replace(text, "$1")
            output.append(text).append('\n')
        }
        return output.toString().trim()
    }
}
