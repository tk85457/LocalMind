package com.localmind.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.ui.theme.NeonText

// Streaming ke dauran incomplete code block close karo (non-streaming only)
fun closeIncompleteCodeBlocks(text: String): String {
    var count = 0
    var i = 0
    while (i <= text.length - 3) {
        if (text[i] == '`' && text[i + 1] == '`' && text[i + 2] == '`') { count++; i += 3 } else i++
    }
    return if (count % 2 != 0) "$text\n```" else text
}

private sealed class MdSegment {
    data class Text(val content: String) : MdSegment()
    data class Code(val language: String, val code: String) : MdSegment()
}

private fun parseSegments(markdown: String, isStreaming: Boolean = false): List<MdSegment> {
    if (!markdown.contains("```")) return listOf(MdSegment.Text(markdown))

    val segments = mutableListOf<MdSegment>()
    var pos = 0
    val len = markdown.length

    while (pos < len) {
        val fenceStart = markdown.indexOf("```", pos)
        if (fenceStart == -1) {
            val text = markdown.substring(pos).trim()
            if (text.isNotEmpty()) segments.add(MdSegment.Text(text))
            break
        }

        if (fenceStart > pos) {
            val text = markdown.substring(pos, fenceStart).trim()
            if (text.isNotEmpty()) segments.add(MdSegment.Text(text))
        }

        val afterFence = fenceStart + 3
        val langEnd = markdown.indexOf('\n', afterFence)

        if (langEnd == -1) {
            if (isStreaming) {
                segments.add(MdSegment.Code(markdown.substring(afterFence).trim(), ""))
            }
            break
        }

        val lang = markdown.substring(afterFence, langEnd).trim()
        val codeStart = langEnd + 1
        val closingFence = markdown.indexOf("```", codeStart)

        if (closingFence == -1) {
            if (isStreaming) {
                val rawCode = markdown.substring(codeStart)
                    .trimEnd('\u258c')
                    .trimEnd('\n')
                segments.add(MdSegment.Code(lang, rawCode))
            } else {
                val text = markdown.substring(fenceStart).trim()
                if (text.isNotEmpty()) segments.add(MdSegment.Text(text))
            }
            break
        }

        segments.add(MdSegment.Code(lang, markdown.substring(codeStart, closingFence).trimEnd('\n')))
        pos = closingFence + 3
    }

    return if (segments.isEmpty()) listOf(MdSegment.Text(markdown)) else segments
}

/**
 * MarkdownText — pure Compose Text() only.
 * Code blocks = CodeBlock composable (apna highlight engine).
 * Koi Markwon/AndroidView nahi — zero overhead, no markdown formatting on plain text.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = NeonText,
    fontSize: Float = 16f,
    isSelectable: Boolean = true,
    isStreaming: Boolean = false
) {
    val renderText = if (isStreaming) markdown else closeIncompleteCodeBlocks(markdown)

    val segments = if (isStreaming) {
        parseSegments(renderText, isStreaming = true)
    } else {
        remember(renderText) { parseSegments(renderText, isStreaming = false) }
    }

    // Fast path: single plain text segment
    if (segments.size == 1 && segments[0] is MdSegment.Text) {
        val text = (segments[0] as MdSegment.Text).content
        if (isSelectable) {
            SelectionContainer(modifier = modifier) {
                Text(text = text, color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
            }
        } else {
            Text(text = text, modifier = modifier, color = color, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
        }
        return
    }

    // Mixed: text + code blocks
    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is MdSegment.Text -> {
                    if (segment.content.isNotBlank()) {
                        if (isSelectable) {
                            SelectionContainer {
                                Text(
                                    text = segment.content,
                                    color = color,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.5f).sp
                                )
                            }
                        } else {
                            Text(
                                text = segment.content,
                                color = color,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.5f).sp
                            )
                        }
                    }
                }
                is MdSegment.Code -> {
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    CodeBlock(
                        code = segment.code,
                        language = segment.language,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index < segments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
