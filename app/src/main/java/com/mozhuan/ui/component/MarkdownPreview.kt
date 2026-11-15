package com.mozhuan.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Ordered(val number: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val dividerRegex = Regex("""^([-*_])(?:\s*\1){2,}$""")
private val bulletRegex = Regex("""^[-*+]\s+(.*)$""")
private val orderedRegex = Regex("""^(\d+)[.)]\s+(.*)$""")
private val fenceRegex = Regex("""^```(.*)$""")

fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val codeLines = mutableListOf<String>()
    var codeLanguage: String? = null
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    source.lineSequence().forEach { rawLine ->
        val trimmed = rawLine.trim()
        if (inCode) {
            if (trimmed.startsWith("```")) {
                blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
                codeLines.clear()
                codeLanguage = null
                inCode = false
            } else {
                codeLines += rawLine
            }
            return@forEach
        }
        val fence = fenceRegex.find(trimmed)
        if (fence != null) {
            flushParagraph()
            inCode = true
            codeLanguage = fence.groupValues[1].trim().ifBlank { null }
            return@forEach
        }
        when {
            trimmed.isEmpty() -> flushParagraph()
            headingRegex.matches(trimmed) -> {
                flushParagraph()
                val match = headingRegex.find(trimmed)!!
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim(),
                )
            }

            dividerRegex.matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Divider
            }

            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(trimmed.removePrefix(">").trimStart())
            }

            bulletRegex.matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bulletRegex.find(trimmed)!!.groupValues[1])
            }

            orderedRegex.matches(trimmed) -> {
                flushParagraph()
                val match = orderedRegex.find(trimmed)!!
                blocks += MarkdownBlock.Ordered(match.groupValues[1], match.groupValues[2])
            }

            else -> paragraph += rawLine
        }
    }
    flushParagraph()
    if (inCode && codeLines.isNotEmpty()) {
        blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
    }
    return blocks
}

@Composable
fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (blocks.isEmpty()) {
            Text(
                text = "（空章节）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inlineAnnotated(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MarkdownBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.text),
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 27.sp),
                )

                is MarkdownBlock.Bullet -> Row(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = inlineAnnotated(block.text),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    )
                }

                is MarkdownBlock.Ordered -> Row(modifier = Modifier.padding(start = 6.dp)) {
                    Text(
                        text = "${block.number}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = inlineAnnotated(block.text),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    )
                }

                is MarkdownBlock.Quote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Text(
                            text = inlineAnnotated(block.text),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is MarkdownBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                        ),
                    )
                }

                MarkdownBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun inlineAnnotated(text: String): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val rest = text.substring(index)
            when {
                rest.startsWith("`") -> {
                    val end = rest.indexOf('`', startIndex = 1)
                    if (end > 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                            append(rest.substring(1, end))
                        }
                        index += end + 1
                    } else {
                        append(rest[0]); index++
                    }
                }

                rest.startsWith("**") || rest.startsWith("__") -> {
                    val marker = rest.substring(0, 2)
                    val end = rest.indexOf(marker, startIndex = 2)
                    if (end > 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(rest.substring(2, end))
                        }
                        index += end + 2
                    } else {
                        append(rest[0]); index++
                    }
                }

                rest.startsWith("~~") -> {
                    val end = rest.indexOf("~~", startIndex = 2)
                    if (end > 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(rest.substring(2, end))
                        }
                        index += end + 2
                    } else {
                        append(rest[0]); index++
                    }
                }

                rest.startsWith("*") || rest.startsWith("_") -> {
                    val end = rest.indexOf(rest[0], startIndex = 1)
                    if (end > 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(rest.substring(1, end))
                        }
                        index += end + 1
                    } else {
                        append(rest[0]); index++
                    }
                }

                rest.startsWith("[") -> {
                    val labelEnd = rest.indexOf("](")
                    val urlEnd = if (labelEnd > 0) rest.indexOf(')', startIndex = labelEnd + 2) else -1
                    if (labelEnd > 0 && urlEnd > labelEnd) {
                        withStyle(
                            SpanStyle(color = primary, textDecoration = TextDecoration.Underline),
                        ) {
                            append(rest.substring(1, labelEnd))
                        }
                        index += urlEnd + 1
                    } else {
                        append(rest[0]); index++
                    }
                }

                else -> {
                    append(rest[0]); index++
                }
            }
        }
    }
}
