package me.eroi.lolidaily.muzei.util

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import me.eroi.lolidaily.muzei.ui.screen.components.NormalInlineImage
import me.eroi.lolidaily.muzei.ui.screen.components.PixelInlineImage
import me.eroi.lolidaily.muzei.ui.screen.components.SmileyMapper

internal sealed interface CommentBlock {
    data class Text(
        val annotatedString: AnnotatedString,
        val inlineContent: Map<String, InlineTextContent>,
        val spoilerRanges: List<IntRange>
    ) : CommentBlock

    data class Image(
        val url: String
    ) : CommentBlock

    data class Quote(
        val blocks: List<CommentBlock>
    ) : CommentBlock
}

internal object BBCodeParser {
    sealed interface Token {
        data class TagOpen(val name: String, val arg: String?, val raw: String) : Token
        data class TagClose(val name: String, val raw: String) : Token
        data class Smiley(val code: String, val raw: String) : Token
        data class Text(val content: String) : Token
    }

    private val TOKEN_REGEX = Regex(
        """(\[/?(?:url|user|img|quote|b|i|u|s|mask|code|color|size|right|center|left)(?:=[^]]*)?])|\((bgm\d+|musume_\d+|blake_\d+)\)""",
        RegexOption.IGNORE_CASE
    )

    private val MENTION_REGEX = Regex("""@[\w一-鿿]+""")

    private fun parseTag(raw: String): Token {
        val inner = raw.removeSurrounding("[", "]")
        if (inner.startsWith("/")) {
            val name = inner.removePrefix("/").lowercase()
            return Token.TagClose(name, raw)
        }
        val parts = inner.split('=', limit = 2)
        val name = parts[0].lowercase()
        val arg = parts.getOrNull(1)
        return Token.TagOpen(name, arg, raw)
    }

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var lastIndex = 0
        for (match in TOKEN_REGEX.findAll(input)) {
            if (match.range.first > lastIndex) {
                tokens += Token.Text(input.substring(lastIndex, match.range.first))
            }
            val group1 = match.groups[1]?.value
            val group2 = match.groups[2]?.value
            if (group1 != null) {
                tokens += parseTag(group1)
            } else if (group2 != null) {
                tokens += Token.Smiley(group2, match.value)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) {
            tokens += Token.Text(input.substring(lastIndex))
        }
        return tokens
    }

    fun highlightMentions(builder: AnnotatedString.Builder, text: String, tertiaryColor: Color) {
        for (match in MENTION_REGEX.findAll(text)) {
            builder.addStyle(
                SpanStyle(color = tertiaryColor, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1
            )
        }
    }

    data class TagState(val name: String, val arg: String?, val start: Int)
    data class StyleSpan(val tag: TagState, val range: IntRange)

    fun parse(
        input: String,
        bgmDomain: String,
        tertiaryColor: Color
    ): List<CommentBlock> {
        val tokens = tokenize(input)
        val blockListsStack = mutableListOf<MutableList<CommentBlock>>(mutableListOf())
        fun currentBlocks() = blockListsStack.last()

        var builder = AnnotatedString.Builder()
        val activeTags = mutableListOf<TagState>()
        val closedSpans = mutableListOf<StyleSpan>()
        val inlineContent = mutableMapOf<String, InlineTextContent>()
        val spoilerRanges = mutableListOf<IntRange>()
        var imageCounter = 0

        fun closeActiveTags(tagIndex: Int) {
            while (activeTags.size > tagIndex) {
                val poppedTag = activeTags.removeAt(activeTags.size - 1)
                val end = builder.length
                closedSpans += StyleSpan(poppedTag, poppedTag.start until end)
            }
        }

        fun flushTextBlock() {
            val textStr = builder.toAnnotatedString().text
            if (textStr.isNotEmpty() || inlineContent.isNotEmpty() || closedSpans.isNotEmpty() || activeTags.isNotEmpty()) {
                val finalAnnotatedString = buildAnnotatedString {
                    append(textStr)

                    val oldAnnotated = builder.toAnnotatedString()
                    for (annotation in oldAnnotated.getStringAnnotations(
                        "androidx.compose.foundation.text.inlineContent",
                        0,
                        textStr.length
                    )) {
                        addStringAnnotation(
                            tag = "androidx.compose.foundation.text.inlineContent",
                            annotation = annotation.item,
                            start = annotation.start,
                            end = annotation.end
                        )
                    }

                    for (span in closedSpans) {
                        applyTagStyle(
                            span.tag,
                            span.range.first,
                            span.range.last + 1,
                            textStr,
                            bgmDomain,
                            spoilerRanges,
                        )
                    }

                    for (tag in activeTags) {
                        applyTagStyle(tag, tag.start, textStr.length, textStr, bgmDomain, spoilerRanges)
                    }

                    highlightMentions(this, textStr, tertiaryColor)
                }

                currentBlocks() += CommentBlock.Text(
                    annotatedString = finalAnnotatedString,
                    inlineContent = inlineContent.toMap(),
                    spoilerRanges = spoilerRanges.toList()
                )

                builder = AnnotatedString.Builder()
                closedSpans.clear()
                inlineContent.clear()
                spoilerRanges.clear()

                for (j in activeTags.indices) {
                    activeTags[j] = activeTags[j].copy(start = 0)
                }
            }
        }

        var i = 0
        while (i < tokens.size) {
            when (val token = tokens[i]) {
                is Token.TagOpen -> {
                    when (token.name) {
                        "user" -> {
                            val tagState = TagState(token.name, token.arg, builder.length)
                            builder.append("@")
                            activeTags.add(tagState)
                            i++
                        }
                        "img" -> {
                            val nextToken = tokens.getOrNull(i + 1)
                            val afterNextToken = tokens.getOrNull(i + 2)
                            if (nextToken is Token.Text && afterNextToken is Token.TagClose && afterNextToken.name == "img") {
                                flushTextBlock()
                                val url = nextToken.content.trim()
                                currentBlocks() += CommentBlock.Image(url)
                                i += 3
                                continue
                            }
                            val tagState = TagState(token.name, token.arg, builder.length)
                            activeTags.add(tagState)
                            i++
                        }
                        "quote" -> {
                            flushTextBlock()
                            val quoteBlocks = mutableListOf<CommentBlock>()
                            blockListsStack.add(quoteBlocks)
                            i++
                        }
                        else -> {
                            val tagState = TagState(token.name, token.arg, builder.length)
                            activeTags.add(tagState)
                            i++
                        }
                    }
                }
                is Token.TagClose -> {
                    if (token.name == "quote") {
                        if (blockListsStack.size > 1) {
                            flushTextBlock()
                            val inner = blockListsStack.removeAt(blockListsStack.size - 1)
                            currentBlocks() += CommentBlock.Quote(inner)
                        }
                    } else {
                        val matchIndex = activeTags.indexOfLast { it.name == token.name }
                        if (matchIndex != -1) {
                            closeActiveTags(matchIndex)
                        }
                    }
                    i++
                }
                is Token.Smiley -> {
                    val (url, isPixelArt) = SmileyMapper.resolve(token.code, bgmDomain) ?: (null to false)
                    if (url != null) {
                        val id = "img_${imageCounter++}"
                        builder.appendInlineContent(id, "(${token.code})")

                        val size = if (isPixelArt) 20.sp else 60.sp
                        inlineContent[id] = InlineTextContent(
                            Placeholder(
                                width = size,
                                height = size,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Bottom
                            )
                        ) {
                            if (isPixelArt) {
                                PixelInlineImage(url)
                            } else {
                                NormalInlineImage(url)
                            }
                        }
                    }
                    i++
                }
                is Token.Text -> {
                    builder.append(token.content)
                    i++
                }
            }
        }

        flushTextBlock()
        while (blockListsStack.size > 1) {
            val inner = blockListsStack.removeAt(blockListsStack.size - 1)
            currentBlocks() += CommentBlock.Quote(inner)
        }
        return blockListsStack[0]
    }

    private fun AnnotatedString.Builder.applyTagStyle(
        tag: TagState,
        start: Int,
        end: Int,
        textStr: String,
        bgmDomain: String,
        spoilerRanges: MutableList<IntRange>,
    ) {
        if (start >= end) return

        when (tag.name) {
            "b" -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            "i" -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            "u" -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            "s" -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            "code" -> {
                addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.LightGray.copy(alpha = 0.2f)
                    ),
                    start,
                    end
                )
            }
            "url" -> {
                val url = tag.arg ?: textStr.substring(start, end).trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = Color(0xFF1E88E5),
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ),
                        start,
                        end
                    )
                }
            }
            "user" -> {
                val userId = tag.arg?.trim()
                if (!userId.isNullOrEmpty()) {
                    addLink(
                        LinkAnnotation.Url(
                            url = "https://$bgmDomain/user/$userId",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = Color(0xFF1E88E5),
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                        start,
                        end,
                    )
                }
            }
            "color" -> {
                val colorArg = tag.arg
                if (colorArg != null) {
                    val parsedColor = parseColor(colorArg)
                    if (parsedColor != null) {
                        addStyle(SpanStyle(color = parsedColor), start, end)
                    }
                }
            }
            "size" -> {
                val sizeArg = tag.arg
                if (sizeArg != null) {
                    val parsedSize = parseSize(sizeArg)
                    if (parsedSize != null) {
                        addStyle(SpanStyle(fontSize = parsedSize), start, end)
                    }
                }
            }
            "mask" -> {
                spoilerRanges += start until end
            }
            "center" -> addStyle(ParagraphStyle(textAlign = TextAlign.Center), start, end)
            "right" -> addStyle(ParagraphStyle(textAlign = TextAlign.Right), start, end)
            "left" -> addStyle(ParagraphStyle(textAlign = TextAlign.Left), start, end)
        }
    }

    private fun parseColor(colorStr: String): Color? {
        val clean = colorStr.trim().removePrefix("#")
        val isHex = clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        return try {
            if (isHex && clean.length == 6) {
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                Color(r, g, b)
            } else if (isHex && clean.length == 3) {
                val rStr = clean[0].toString()
                val gStr = clean[1].toString()
                val bStr = clean[2].toString()
                val r = "$rStr$rStr".toInt(16)
                val g = "$gStr$gStr".toInt(16)
                val b = "$bStr$bStr".toInt(16)
                Color(r, g, b)
            } else {
                when (clean.lowercase()) {
                    "red" -> Color.Red
                    "blue" -> Color.Blue
                    "green" -> Color.Green
                    "yellow" -> Color.Yellow
                    "black" -> Color.Black
                    "white" -> Color.White
                    "gray", "grey" -> Color.Gray
                    "cyan" -> Color.Cyan
                    "magenta" -> Color.Magenta
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSize(sizeStr: String): TextUnit? {
        val clean = sizeStr.trim()
        if (clean.endsWith("%")) {
            val pct = clean.removeSuffix("%").toFloatOrNull() ?: return null
            return (pct / 100f).em
        }
        val num = clean.toFloatOrNull() ?: return null
        return if (num <= 5) {
            num.em
        } else {
            num.sp
        }
    }
}
