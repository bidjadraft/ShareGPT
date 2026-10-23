package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletItem(val text: String, val level: Int = 0) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
}

fun isArabicText(text: String): Boolean {
    return text.any {
        it in '\u0600'..'\u06FF' ||
        it in '\u0750'..'\u077F' ||
        it in '\u08A0'..'\u08FF' ||
        it in '\uFB50'..'\uFDFF' ||
        it in '\uFE70'..'\uFEFF'
    }
}

@Composable
fun MarkdownMessageView(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Header -> {
                        val isArabic = remember(block.text) { isArabicText(block.text) }
                        val (style, size) = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge to 19.sp
                            2 -> MaterialTheme.typography.titleMedium to 17.sp
                            else -> MaterialTheme.typography.titleSmall to 15.sp
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Text(
                                text = buildInlineMarkdown(block.text),
                                style = style.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = size,
                                    textDirection = if (isArabic) TextDirection.Rtl else TextDirection.ContentOrRtl,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                    }
                    is MarkdownBlock.CodeBlock -> {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            CodeBlockCard(language = block.language, code = block.code)
                        }
                    }
                    is MarkdownBlock.BulletItem -> {
                        val isArabic = remember(block.text) { isArabicText(block.text) }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (isArabic) 0.dp else (block.level * 12).dp,
                                        end = if (isArabic) (block.level * 12).dp else 0.dp,
                                        top = 2.dp
                                    ),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                Text(
                                    text = buildInlineMarkdown(block.text),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = if (isArabic) TextDirection.Rtl else TextDirection.ContentOrRtl,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                        lineHeight = 22.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                    is MarkdownBlock.NumberedItem -> {
                        val isArabic = remember(block.text) { isArabicText(block.text) }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${block.number}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                Text(
                                    text = buildInlineMarkdown(block.text),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = if (isArabic) TextDirection.Rtl else TextDirection.ContentOrRtl,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                        lineHeight = 22.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                    is MarkdownBlock.Paragraph -> {
                        val isArabic = remember(block.text) { isArabicText(block.text) }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Text(
                                text = buildInlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = if (isArabic) TextDirection.Rtl else TextDirection.ContentOrRtl,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                    lineHeight = 22.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141419),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282832)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar with language tag and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA0A0AB),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFFA0A0AB),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Scrollable code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE4E4EB),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block check
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeBuilder.appendLine(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeBuilder.toString().trimEnd()))
            i++
            continue
        }

        // Header check
        if (line.startsWith("#")) {
            val count = line.takeWhile { it == '#' }.length
            if (count in 1..4 && line.length > count && line[count] == ' ') {
                val headerText = line.substring(count + 1).trim()
                blocks.add(MarkdownBlock.Header(count, headerText))
                i++
                continue
            }
        }

        // Bullet item check
        val trimmed = line.trim()
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
            val bulletText = trimmed.substring(2).trim()
            val indent = line.takeWhile { it == ' ' }.length / 2
            blocks.add(MarkdownBlock.BulletItem(bulletText, indent.coerceAtMost(3)))
            i++
            continue
        }

        // Numbered list check (e.g. "1. ")
        val numMatch = Regex("^([0-9]{1,3})\\.\\s+(.+)").find(trimmed)
        if (numMatch != null) {
            val num = numMatch.groupValues[1]
            val text = numMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, text))
            i++
            continue
        }

        // Blank lines
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Regular paragraph (group non-empty consecutive lines)
        val paraBuilder = StringBuilder(line)
        i++
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].trimStart().startsWith("```") &&
            !lines[i].startsWith("#") &&
            !lines[i].trim().startsWith("* ") &&
            !lines[i].trim().startsWith("- ") &&
            !Regex("^([0-9]{1,3})\\.\\s+").containsMatchIn(lines[i].trim())
        ) {
            paraBuilder.append(" ").append(lines[i].trim())
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraBuilder.toString().trim()))
    }

    return blocks
}

fun buildInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Check for inline code `...`
            if (text[cursor] == '`') {
                val nextBacktick = text.indexOf('`', cursor + 1)
                if (nextBacktick != -1) {
                    val codeContent = text.substring(cursor + 1, nextBacktick)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF282832),
                            color = Color(0xFF9E86FF),
                            fontSize = 13.sp
                        )
                    ) {
                        append(" $codeContent ")
                    }
                    cursor = nextBacktick + 1
                    continue
                }
            }

            // Check for bold **...**
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val nextBold = text.indexOf("**", cursor + 2)
                if (nextBold != -1) {
                    val boldContent = text.substring(cursor + 2, nextBold)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldContent)
                    }
                    cursor = nextBold + 2
                    continue
                }
            }

            // Check for italic *...*
            if (text[cursor] == '*') {
                val nextStar = text.indexOf('*', cursor + 1)
                if (nextStar != -1 && (nextStar == cursor + 1 || text[nextStar - 1] != ' ')) {
                    val italicContent = text.substring(cursor + 1, nextStar)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italicContent)
                    }
                    cursor = nextStar + 1
                    continue
                }
            }

            append(text[cursor])
            cursor++
        }
    }
}
