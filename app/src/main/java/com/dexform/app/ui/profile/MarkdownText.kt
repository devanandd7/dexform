package com.dexform.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Parsed Markdown AST nodes.
 * Parsing is executed in pure Kotlin outside the Composable tree
 * to prevent Compose compiler group mismatch (Start/end imbalance).
 */
sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Numbered(val numberPrefix: String, val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    object Blank : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

/**
 * Pure Kotlin Markdown parser.
 */
fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    if (markdown.isEmpty()) return emptyList()

    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var inCodeBlock = false
    val codeBlockContent = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeBlockContent.toString().trimEnd()))
                codeBlockContent.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeBlockContent.clear()
            }
            continue
        }

        if (inCodeBlock) {
            codeBlockContent.appendLine(line)
            continue
        }

        when {
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(level = 3, text = trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(level = 2, text = trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(level = 1, text = trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("---") || trimmed.startsWith("***") || trimmed.startsWith("___") -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                blocks.add(MarkdownBlock.Bullet(text = trimmed.substring(2).trim()))
            }
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val dotIndex = trimmed.indexOf('.')
                val numberPrefix = trimmed.substring(0, dotIndex + 2)
                val content = trimmed.substring(dotIndex + 2).trim()
                blocks.add(MarkdownBlock.Numbered(numberPrefix = numberPrefix, text = content))
            }
            trimmed.isEmpty() -> {
                blocks.add(MarkdownBlock.Blank)
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(text = line))
            }
        }
    }

    if (inCodeBlock && codeBlockContent.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeBlockContent.toString().trimEnd()))
    }

    return blocks
}

/**
 * Lightweight, zero-dependency Markdown renderer for Compose.
 * Renders headings, bold, italic, bullets, numbered lists, inline code, and fenced code blocks.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, fontWeight, topPadding) = when (block.level) {
                        1 -> Triple(20.sp, FontWeight.ExtraBold, 10.dp)
                        2 -> Triple(18.sp, FontWeight.Bold, 8.dp)
                        else -> Triple(16.sp, FontWeight.Bold, 6.dp)
                    }
                    Spacer(modifier = Modifier.height(topPadding))
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(code = block.code)
                }
                is MarkdownBlock.HorizontalRule -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                is MarkdownBlock.Bullet -> {
                    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(text = "• ", fontWeight = FontWeight.Bold, color = textColor)
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.Numbered -> {
                    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                        Text(text = block.numberPrefix, fontWeight = FontWeight.SemiBold, color = textColor)
                        Text(
                            text = parseInlineMarkdown(block.text),
                            fontSize = 14.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.Blank -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        // Streaming dots indicator
        if (isStreaming) {
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "● ● ●",
                    fontSize = 10.sp,
                    color = Color(0xFFE5533D).copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CodeBlockCard(code: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = code,
            color = Color(0xFFE0E0E0),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}

/**
 * Parses inline formatting: bold (**text**), italic (*text*), inline code (`code`).
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Inline code: `code`
                text[i] == '`' -> {
                    val nextBacktick = text.indexOf('`', i + 1)
                    if (nextBacktick != -1) {
                        val codeSnippet = text.substring(i + 1, nextBacktick)
                        val start = length
                        append(codeSnippet)
                        addStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0x33888888),
                                fontSize = 12.sp
                            ),
                            start,
                            length
                        )
                        i = nextBacktick + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val nextDoubleStar = text.indexOf("**", i + 2)
                    if (nextDoubleStar != -1) {
                        val boldSnippet = text.substring(i + 2, nextDoubleStar)
                        val start = length
                        append(boldSnippet)
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                        i = nextDoubleStar + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text* (single star)
                text[i] == '*' && (i == 0 || text[i - 1] != '*') && (i + 1 < text.length && text[i + 1] != '*') -> {
                    val nextStar = text.indexOf('*', i + 1)
                    if (nextStar != -1 && (nextStar + 1 >= text.length || text[nextStar + 1] != '*')) {
                        val italicSnippet = text.substring(i + 1, nextStar)
                        val start = length
                        append(italicSnippet)
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                        i = nextStar + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
