package com.localmind.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localmind.app.ui.theme.NeonPrimary
import com.localmind.app.ui.theme.NeonTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colour palette (PocketPal atomOneDark equivalent) ──────────────────────
private val BG_HEADER   = Color(0xFF1C1C2E)   // header bar
private val BG_CODE     = Color(0xFF0D1117)   // code body (GitHub dark)
private val C_DEFAULT   = Color(0xFFE6EDF3)   // plain text
private val C_KEYWORD   = Color(0xFFFF7B72)   // red  – keywords
private val C_STRING    = Color(0xFFA5D6FF)   // blue – strings
private val C_COMMENT   = Color(0xFF8B949E)   // grey – comments
private val C_NUMBER    = Color(0xFFD2A8FF)   // purple – numbers
private val C_FUNCTION  = Color(0xFFD2A8FF)   // purple – function names
private val C_TYPE      = Color(0xFFFFA657)   // orange – types/annotations
private val C_OPERATOR  = Color(0xFFFF7B72)   // red  – operators

/**
 * PocketPal-style code block:
 *   ┌────────────────────────────────┐
 *   │ kotlin                 [copy] │   ← header bar
 *   ├────────────────────────────────┤
 *   │ fun hello() {                  │   ← syntax-highlighted, scrollable
 *   │   println("hi")               │
 *   │ }                              │
 *   └────────────────────────────────┘
 */
@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    var copied  by remember { mutableStateOf(false) }

    val iconTint by animateColorAsState(
        targetValue = if (copied) NeonPrimary else NeonTextSecondary,
        animationSpec = tween(300),
        label = "copyIconTint"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        // ── Header bar (language + copy button) ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BG_HEADER)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp
                ),
                color = NeonTextSecondary
            )

            IconButton(
                onClick = {
                    copyToClipboard(context, code.trim())
                    copied = true
                    scope.launch {
                        delay(2000)
                        copied = false
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (copied) "Copied" else "Copy code",
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── Code body (syntax-highlighted, horizontally scrollable) ────────
        // highlight() ko directly call karo — Compose khud recompose tabhi karta hai jab
        // code param change hota hai, isliye extra remember caching ki zaroorat nahi.
        val highlighted = highlight(code, language)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BG_CODE)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = highlighted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                softWrap = false   // horizontal scroll karo, wrap mat karo
            )
        }
    }
}

// ── Clipboard helper ────────────────────────────────────────────────────────
private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
}

// ── Lightweight syntax highlighter ─────────────────────────────────────────
// Pure Kotlin — no extra library needed.
// Covers the most common tokens across Kotlin/Java/Python/JS/C/C++/Bash/JSON.
// Returns an AnnotatedString with coloured spans.
private fun highlight(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
    val lang = language.lowercase().trim()

    // Languages that need no highlighting (plain text / shell output)
    if (lang in setOf("text", "txt", "output", "plaintext", "")) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = C_DEFAULT)) { append(code) }
        }
    }

    // ── Token rules (order matters — more specific first) ──────────────────
    data class Rule(val regex: Regex, val color: Color)

    val rules: List<Rule> = when {
        // ── Python ──────────────────────────────────────────────────────────
        lang == "python" || lang == "py" -> listOf(
            Rule(Regex("""#[^\n]*"""),                                     C_COMMENT),
            Rule(Regex("""\"\"\"[\s\S]*?\"\"\"|\'\'\'[\s\S]*?\'\'\'"""),   C_STRING),
            Rule(Regex("""\"[^\"\\]*(?:\\.[^\"\\]*)*\"|\'[^\'\\]*(?:\\.[^\'\\]*)*\'"""), C_STRING),
            Rule(Regex("""\b(def|class|import|from|as|return|if|elif|else|for|while|in|not|and|or|is|None|True|False|with|try|except|finally|raise|yield|lambda|pass|break|continue|del|global|nonlocal|assert)\b"""), C_KEYWORD),
            Rule(Regex("""\b(int|float|str|bool|list|dict|set|tuple|type|object|bytes|print|range|len|open)\b"""), C_TYPE),
            Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b"""),                   C_TYPE),
            Rule(Regex("""\b\d+\.?\d*\b"""),                              C_NUMBER),
        )

        // ── Kotlin / Java ───────────────────────────────────────────────────
        lang in setOf("kotlin", "kt", "java") -> listOf(
            Rule(Regex("""//[^\n]*"""),                                    C_COMMENT),
            Rule(Regex("""/\*[\s\S]*?\*/"""),                             C_COMMENT),
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*""""),                    C_STRING),
            Rule(Regex("""'[^'\\]*(?:\\.[^'\\]*)*'"""),                   C_STRING),
            Rule(Regex("""\b(fun|val|var|class|object|interface|if|else|when|for|while|do|return|import|package|override|suspend|private|public|protected|internal|open|abstract|data|sealed|companion|by|in|is|as|try|catch|finally|throw|true|false|null|this|super|constructor|init|typealias|enum|annotation|inline|infix|operator|reified|crossinline|noinline|tailrec|external|expect|actual)\b"""), C_KEYWORD),
            Rule(Regex("""@[A-Za-z][A-Za-z0-9_]*"""),                     C_TYPE),
            Rule(Regex("""\b(String|Int|Long|Double|Float|Boolean|Char|Byte|Short|Unit|Any|Nothing|List|Map|Set|Array|MutableList|MutableMap|MutableSet|Flow|StateFlow|ViewModel|Context|Modifier|Composable)\b"""), C_TYPE),
            Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b"""),                   C_TYPE),
            Rule(Regex("""\b\d+[LlFfDd]?\b"""),                          C_NUMBER),
        )

        // ── JavaScript / TypeScript ─────────────────────────────────────────
        lang in setOf("javascript", "js", "typescript", "ts", "jsx", "tsx") -> listOf(
            Rule(Regex("""//[^\n]*"""),                                    C_COMMENT),
            Rule(Regex("""/\*[\s\S]*?\*/"""),                             C_COMMENT),
            Rule(Regex("""`[^`]*`"""),                                     C_STRING),
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*'"""), C_STRING),
            Rule(Regex("""\b(const|let|var|function|class|return|if|else|for|while|do|switch|case|break|continue|import|export|default|from|async|await|try|catch|finally|throw|new|this|typeof|instanceof|in|of|true|false|null|undefined|void|delete|yield)\b"""), C_KEYWORD),
            Rule(Regex("""\b(string|number|boolean|any|void|never|unknown|object|Array|Promise|Record|Partial|Required|Readonly)\b"""), C_TYPE),
            Rule(Regex("""\b([A-Z][A-Za-z0-9_]*)\b"""),                   C_TYPE),
            Rule(Regex("""\b\d+\.?\d*\b"""),                              C_NUMBER),
        )

        // ── C / C++ ─────────────────────────────────────────────────────────
        lang in setOf("c", "cpp", "c++", "h", "hpp") -> listOf(
            Rule(Regex("""//[^\n]*"""),                                    C_COMMENT),
            Rule(Regex("""/\*[\s\S]*?\*/"""),                             C_COMMENT),
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*""""),                    C_STRING),
            Rule(Regex("""'[^'\\]*(?:\\.[^'\\]*)*'"""),                   C_STRING),
            Rule(Regex("""#\s*(include|define|ifdef|ifndef|endif|pragma|if|else|elif)[^\n]*"""), C_TYPE),
            Rule(Regex("""\b(auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|inline|int|long|register|restrict|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|nullptr|true|false|class|public|private|protected|virtual|override|namespace|using|template|typename)\b"""), C_KEYWORD),
            Rule(Regex("""\b\d+\.?\d*[fFlL]?\b"""),                      C_NUMBER),
        )

        // ── Bash / Shell ────────────────────────────────────────────────────
        lang in setOf("bash", "sh", "shell", "zsh") -> listOf(
            Rule(Regex("""#[^\n]*"""),                                     C_COMMENT),
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*'"""), C_STRING),
            Rule(Regex("""\$\{?[A-Za-z_][A-Za-z0-9_]*\}?"""),             C_TYPE),
            Rule(Regex("""\b(if|then|else|elif|fi|for|while|do|done|case|esac|function|in|return|export|local|readonly|echo|cd|ls|grep|sed|awk|cat|rm|mkdir|cp|mv)\b"""), C_KEYWORD),
            Rule(Regex("""\b\d+\b"""),                                    C_NUMBER),
        )

        // ── JSON ─────────────────────────────────────────────────────────────
        lang == "json" -> listOf(
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*"\s*:"""),               C_FUNCTION),  // keys
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*""""),                    C_STRING),    // string values
            Rule(Regex("""\b(true|false|null)\b"""),                      C_KEYWORD),
            Rule(Regex("""-?\d+\.?\d*([eE][+-]?\d+)?"""),                C_NUMBER),
        )

        // ── CSS / SCSS / LESS ─────────────────────────────────────────────────
        lang in setOf("css", "scss", "less", "sass") -> listOf(
            Rule(Regex("""(/\*[\s\S]*?\*/)"""),                            C_COMMENT),
            Rule(Regex("""//[^\n]*"""),                                    C_COMMENT),
            // Strings
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*'"""), C_STRING),
            // At-rules: @media @keyframes @import @mixin etc
            Rule(Regex("""@[a-zA-Z][\w-]*"""),                             C_TYPE),
            // Property values: hex colours
            Rule(Regex("""#[0-9a-fA-F]{3,8}\b"""),                         C_NUMBER),
            // Numbers with units: 16px, 1.5rem, 100%, 0.5s
            Rule(Regex("""\b\d+\.?\d*(px|em|rem|vh|vw|vmin|vmax|%|s|ms|deg|fr|ch|ex|pt|pc|cm|mm|in)?\b"""), C_NUMBER),
            // CSS variables: --primary-color
            Rule(Regex("""--[\w-]+"""),                                    C_FUNCTION),
            // SCSS variables: $primary
            Rule(Regex("""\$[\w-]+"""),                                    C_FUNCTION),
            // LESS variables: @primary (after @-rules so they don't clash)
            Rule(Regex("""@[\w-]+(?=\s*:)"""),                             C_FUNCTION),
            // Pseudo-classes & pseudo-elements: :hover ::before
            Rule(Regex("""::?[a-zA-Z][\w-]*"""),                           C_KEYWORD),
            // Important keyword
            Rule(Regex("""!important\b"""),                                C_KEYWORD),
            // Property names (word before colon inside a rule block)
            Rule(Regex("""(?<=^|;|\{)\s*([\w-]+)(?=\s*:)"""),              C_TYPE),
            // Common value keywords
            Rule(Regex("""\b(none|auto|inherit|initial|unset|revert|normal|bold|italic|flex|grid|block|inline|inline-block|inline-flex|inline-grid|absolute|relative|fixed|sticky|static|visible|hidden|scroll|overflow|center|left|right|top|bottom|middle|baseline|space-between|space-around|space-evenly|stretch|wrap|nowrap|row|column|solid|dashed|dotted|transparent|currentColor|sans-serif|serif|monospace|pointer|default|text|move|not-allowed|zoom-in|zoom-out)\b"""), C_KEYWORD),
            // Selectors: element, .class, #id
            Rule(Regex("""(?<![\w-])\.[-_a-zA-Z][\w-]*"""),               C_FUNCTION),
            Rule(Regex("""(?<![\w-])#[-_a-zA-Z][\w-]*"""),                C_TYPE),
            // CSS functions: rgba() linear-gradient() var() calc()
            Rule(Regex("""\b([a-zA-Z][\w-]*)(?=\()"""),                   C_FUNCTION),
        )

        // ── XML / HTML ───────────────────────────────────────────────────────
        lang in setOf("xml", "html", "svg") -> listOf(
            Rule(Regex("""<!--[\s\S]*?-->"""),                             C_COMMENT),
            Rule(Regex(""""[^"]*"|'[^']*'"""),                            C_STRING),
            Rule(Regex("""</?[A-Za-z][A-Za-z0-9_.-]*"""),                 C_KEYWORD),
            Rule(Regex("""\b[A-Za-z][A-Za-z0-9_.-]*(?=\s*=)"""),         C_TYPE),
        )

        // ── SQL ──────────────────────────────────────────────────────────────
        lang == "sql" -> listOf(
            Rule(Regex("""--[^\n]*"""),                                    C_COMMENT),
            Rule(Regex("""'[^'\\]*(?:\\.[^'\\]*)*'"""),                   C_STRING),
            Rule(Regex("""\b(SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|BY|ORDER|HAVING|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|INDEX|DROP|ALTER|ADD|COLUMN|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|AND|OR|IN|IS|AS|DISTINCT|LIMIT|OFFSET|UNION|ALL|EXISTS|CASE|WHEN|THEN|ELSE|END)\b""", RegexOption.IGNORE_CASE), C_KEYWORD),
            Rule(Regex("""\b\d+\.?\d*\b"""),                              C_NUMBER),
        )

        // ── Fallback: generic highlight ──────────────────────────────────────
        else -> listOf(
            Rule(Regex("""//[^\n]*|#[^\n]*"""),                           C_COMMENT),
            Rule(Regex("""/\*[\s\S]*?\*/"""),                             C_COMMENT),
            Rule(Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*'"""), C_STRING),
            Rule(Regex("""\b(if|else|for|while|return|function|class|import|export|def|var|let|const|val|fun)\b"""), C_KEYWORD),
            Rule(Regex("""\b\d+\.?\d*\b"""),                              C_NUMBER),
        )
    }

    // ── Apply rules to produce coloured spans ──────────────────────────────
    // Build a flat list of (start, end, color) spans, then render
    data class Span(val start: Int, val end: Int, val color: Color)

    val spans = mutableListOf<Span>()
    val claimed = BooleanArray(code.length)   // track already-coloured chars

    for (rule in rules) {
        for (match in rule.regex.findAll(code)) {
            val s = match.range.first
            val e = match.range.last + 1
            // Don't overwrite a span that's already claimed
            if ((s until e).any { claimed[it] }) continue
            for (i in s until e) claimed[i] = true
            spans.add(Span(s, e, rule.color))
        }
    }

    spans.sortBy { it.start }

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.start > cursor) {
                withStyle(SpanStyle(color = C_DEFAULT)) {
                    append(code.substring(cursor, span.start))
                }
            }
            withStyle(SpanStyle(color = span.color)) {
                append(code.substring(span.start, span.end))
            }
            cursor = span.end
        }
        if (cursor < code.length) {
            withStyle(SpanStyle(color = C_DEFAULT)) {
                append(code.substring(cursor))
            }
        }
    }
}
