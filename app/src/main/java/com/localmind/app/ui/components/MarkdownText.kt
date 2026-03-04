package com.localmind.app.ui.components

import android.content.Context
import android.graphics.Color
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.localmind.app.ui.theme.NeonText
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j

/**
 * Markwon-based markdown renderer for the chat screen.
 *
 * Features:
 *  - Code block syntax highlighting via SyntaxHighlightPlugin (Prism4j)
 *  - Text selection enabled so users can copy any word
 *  - Same renderer used for streaming and final messages → no visual jump
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = NeonText,
    fontSize: Float = 16f,
    isSelectable: Boolean = true
) {
    val context = LocalContext.current
    val markwon = remember(context) { createMarkwon(context) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = fontSize
                setTextColor(color.toArgb())
                // Enable text selection → long-press + drag to select, then copy
                setTextIsSelectable(isSelectable)
                // LinkMovementMethod lets links be clickable while selection still works
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.textSize = fontSize
            if (textView.isTextSelectable != isSelectable) {
                textView.setTextIsSelectable(isSelectable)
            }
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}

fun createMarkwon(context: Context): Markwon {
    // Prism4j with GrammarLocatorDef auto-generated class.
    // If annotation processing hasn't run, falls back gracefully (code blocks
    // still styled, just no language-specific coloring).
    val locator = runCatching {
        val clazz = Class.forName("io.noties.prism4j.GrammarLocatorDef")
        clazz.getDeclaredConstructor().newInstance() as GrammarLocator
    }.getOrElse {
        object : GrammarLocator {
            override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? = null
            override fun languages(): Set<String> = emptySet()
        }
    }
    val prism4j = Prism4j(locator)

    return Markwon.builder(context)
        // Syntax-highlighted code blocks (```kotlin, ```python, etc.)
        .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(HtmlPlugin.create())
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(SimpleExtPlugin.create())
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                // Extend span configuration here if needed
            }

            override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                builder
                    .codeBlockBackgroundColor(Color.parseColor("#0D1117")) // GitHub dark style
                    .codeBlockTextColor(Color.parseColor("#E6EDF3"))       // Soft white
                    .codeBackgroundColor(Color.parseColor("#1E1E2E"))      // Inline code bg
                    .codeTextColor(Color.parseColor("#FFD700"))            // Gold for inline code
                    .codeBlockMargin(24)
                    .codeBlockTypeface(android.graphics.Typeface.MONOSPACE)
            }
        })
        .build()
}
