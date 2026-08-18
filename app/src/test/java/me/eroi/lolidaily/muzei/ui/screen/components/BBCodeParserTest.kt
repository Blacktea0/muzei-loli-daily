package me.eroi.lolidaily.muzei.ui.screen.components
import me.eroi.lolidaily.muzei.util.BBCodeParser
import me.eroi.lolidaily.muzei.util.CommentBlock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BBCodeParserTest {

    @Test
    fun testTokenize() {
        val input = "[b]Hello[/b] World [color=#FF0000]red[/color] (bgm01)"
        val tokens = BBCodeParser.tokenize(input)

        // Expected tokens:
        // 0: TagOpen("b", null, "[b]")
        // 1: Text("Hello")
        // 2: TagClose("b", "[/b]")
        // 3: Text(" World ")
        // 4: TagOpen("color", "#FF0000", "[color=#FF0000]")
        // 5: Text("red")
        // 6: TagClose("color", "[/color]")
        // 7: Text(" ")
        // 8: Smiley("bgm01", "(bgm01)")

        assertEquals(9, tokens.size)

        val t0 = tokens[0] as BBCodeParser.Token.TagOpen
        assertEquals("b", t0.name)
        assertEquals(null, t0.arg)
        assertEquals("[b]", t0.raw)

        val t1 = tokens[1] as BBCodeParser.Token.Text
        assertEquals("Hello", t1.content)

        val t2 = tokens[2] as BBCodeParser.Token.TagClose
        assertEquals("b", t2.name)
        assertEquals("[/b]", t2.raw)

        val t3 = tokens[3] as BBCodeParser.Token.Text
        assertEquals(" World ", t3.content)

        val t4 = tokens[4] as BBCodeParser.Token.TagOpen
        assertEquals("color", t4.name)
        assertEquals("#FF0000", t4.arg)
        assertEquals("[color=#FF0000]", t4.raw)

        val t5 = tokens[5] as BBCodeParser.Token.Text
        assertEquals("red", t5.content)

        val t6 = tokens[6] as BBCodeParser.Token.TagClose
        assertEquals("color", t6.name)
        assertEquals("[/color]", t6.raw)

        val t7 = tokens[7] as BBCodeParser.Token.Text
        assertEquals(" ", t7.content)

        val t8 = tokens[8] as BBCodeParser.Token.Smiley
        assertEquals("bgm01", t8.code)
        assertEquals("(bgm01)", t8.raw)
    }

    @Test
    fun testFormattingTags() {
        // Test Bold
        val boldResult = BBCodeParser.parse("[b]Bold Text[/b]", "bangumi.tv", Color.Red)
        val boldAnnotated = (boldResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Bold Text", boldAnnotated.text)
        assertEquals(1, boldAnnotated.spanStyles.size)
        assertEquals(FontWeight.Bold, boldAnnotated.spanStyles[0].item.fontWeight)

        // Test Italic
        val italicResult = BBCodeParser.parse("[i]Italic Text[/i]", "bangumi.tv", Color.Red)
        val italicAnnotated = (italicResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Italic Text", italicAnnotated.text)
        assertEquals(1, italicAnnotated.spanStyles.size)
        assertTrue(FontStyle.Italic == italicAnnotated.spanStyles[0].item.fontStyle)

        // Test Underline
        val underlineResult = BBCodeParser.parse("[u]Underline Text[/u]", "bangumi.tv", Color.Red)
        val underlineAnnotated = (underlineResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Underline Text", underlineAnnotated.text)
        assertEquals(1, underlineAnnotated.spanStyles.size)
        assertEquals(TextDecoration.Underline, underlineAnnotated.spanStyles[0].item.textDecoration)

        // Test Strikethrough
        val strikeResult = BBCodeParser.parse("[s]Strike Text[/s]", "bangumi.tv", Color.Red)
        val strikeAnnotated = (strikeResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Strike Text", strikeAnnotated.text)
        assertEquals(1, strikeAnnotated.spanStyles.size)
        assertEquals(TextDecoration.LineThrough, strikeAnnotated.spanStyles[0].item.textDecoration)

        // Test Code
        val codeResult = BBCodeParser.parse("[code]Code Text[/code]", "bangumi.tv", Color.Red)
        val codeAnnotated = (codeResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Code Text", codeAnnotated.text)
        assertEquals(1, codeAnnotated.spanStyles.size)
        assertEquals(FontFamily.Monospace, codeAnnotated.spanStyles[0].item.fontFamily)
        assertEquals(Color.LightGray.copy(alpha = 0.2f), codeAnnotated.spanStyles[0].item.background)

        // Test Quote
        val quoteResult = BBCodeParser.parse("[quote]Quote Text[/quote]", "bangumi.tv", Color.Red)
        assertTrue(quoteResult.first() is CommentBlock.Quote)
        val quoteBlock = quoteResult.first() as CommentBlock.Quote
        assertEquals(1, quoteBlock.blocks.size)
        assertTrue(quoteBlock.blocks.first() is CommentBlock.Text)
        val quoteText = quoteBlock.blocks.first() as CommentBlock.Text
        assertEquals("Quote Text", quoteText.annotatedString.text)
        // Test Center
        val centerResult = BBCodeParser.parse("[center]Center Text[/center]", "bangumi.tv", Color.Red)
        val centerAnnotated = (centerResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Center Text", centerAnnotated.text)
        assertEquals(1, centerAnnotated.paragraphStyles.size)
        assertEquals(TextAlign.Center, centerAnnotated.paragraphStyles[0].item.textAlign)

        // Test Right
        val rightResult = BBCodeParser.parse("[right]Right Text[/right]", "bangumi.tv", Color.Red)
        val rightAnnotated = (rightResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Right Text", rightAnnotated.text)
        assertEquals(1, rightAnnotated.paragraphStyles.size)
        assertEquals(TextAlign.Right, rightAnnotated.paragraphStyles[0].item.textAlign)

        // Test Left
        val leftResult = BBCodeParser.parse("[left]Left Text[/left]", "bangumi.tv", Color.Red)
        val leftAnnotated = (leftResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Left Text", leftAnnotated.text)
        assertEquals(1, leftAnnotated.paragraphStyles.size)
        assertEquals(TextAlign.Left, leftAnnotated.paragraphStyles[0].item.textAlign)
    }

    @Test
    fun testColorAndSizeParsing() {
        // Color Hex #FF0000
        val hexResult = BBCodeParser.parse("[color=#FF0000]Red[/color]", "bangumi.tv", Color.Red)
        val hexAnnotated = (hexResult.first() as CommentBlock.Text).annotatedString
        assertEquals(Color(0xFFFF0000), hexAnnotated.spanStyles[0].item.color)

        // Color Named red
        val namedResult = BBCodeParser.parse("[color=red]Red[/color]", "bangumi.tv", Color.Red)
        val namedAnnotated = (namedResult.first() as CommentBlock.Text).annotatedString
        assertEquals(Color(0xFFFF0000), namedAnnotated.spanStyles[0].item.color)

        // Color Hex without '#' (like FFFF00)
        val noHashResult = BBCodeParser.parse("[color=FFFF00]Yellow[/color]", "bangumi.tv", Color.Red)
        val noHashAnnotated = (noHashResult.first() as CommentBlock.Text).annotatedString
        assertEquals(Color(0xFFFFFF00), noHashAnnotated.spanStyles[0].item.color)

        // Invalid color argument
        val invalidColorResult = BBCodeParser.parse("[color=invalidColor]Text[/color]", "bangumi.tv", Color.Red)
        val invalidColorAnnotated = (invalidColorResult.first() as CommentBlock.Text).annotatedString
        assertEquals(0, invalidColorAnnotated.spanStyles.size)

        // Size with percentage, e.g. 120% -> 1.2.em
        val percentResult = BBCodeParser.parse("[size=120%]Big[/size]", "bangumi.tv", Color.Red)
        val percentAnnotated = (percentResult.first() as CommentBlock.Text).annotatedString
        assertEquals(1.2f.em, percentAnnotated.spanStyles[0].item.fontSize)

        // Size with em unit <= 5 (like 2.5) -> 2.5.em
        val emResult = BBCodeParser.parse("[size=2.5]Medium[/size]", "bangumi.tv", Color.Red)
        val emAnnotated = (emResult.first() as CommentBlock.Text).annotatedString
        assertEquals(2.5f.em, emAnnotated.spanStyles[0].item.fontSize)

        // Size with sp unit > 5 (like 14) -> 14.sp
        val spResult = BBCodeParser.parse("[size=14]Normal[/size]", "bangumi.tv", Color.Red)
        val spAnnotated = (spResult.first() as CommentBlock.Text).annotatedString
        assertEquals(14.sp, spAnnotated.spanStyles[0].item.fontSize)

        // Invalid size argument
        val invalidSizeResult = BBCodeParser.parse("[size=invalidSize]Text[/size]", "bangumi.tv", Color.Red)
        val invalidSizeAnnotated = (invalidSizeResult.first() as CommentBlock.Text).annotatedString
        assertEquals(0, invalidSizeAnnotated.spanStyles.size)
    }

    @Test
    fun testSpoilerMaskParsing() {
        val result = BBCodeParser.parse("[mask]Secret text[/mask]", "bangumi.tv", Color.Red)
        val textBlock = result.first() as CommentBlock.Text
        assertEquals("Secret text", textBlock.annotatedString.text)
        assertEquals(1, textBlock.spoilerRanges.size)
        assertEquals(0 until 11, textBlock.spoilerRanges[0])

        // Multiple mask tags
        val multiResult = BBCodeParser.parse("[mask]One[/mask] and [mask]Two[/mask]", "bangumi.tv", Color.Red)
        val multiTextBlock = multiResult.first() as CommentBlock.Text
        assertEquals("One and Two", multiTextBlock.annotatedString.text)
        assertEquals(2, multiTextBlock.spoilerRanges.size)
        assertEquals(0 until 3, multiTextBlock.spoilerRanges[0])
        assertEquals(8 until 11, multiTextBlock.spoilerRanges[1])
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    @Test
    fun testClickableUrlLinkAnnotations() {
        // URL as argument
        val argResult = BBCodeParser.parse("[url=https://bgm.tv]Bangumi[/url]", "bangumi.tv", Color.Red)
        val argAnnotated = (argResult.first() as CommentBlock.Text).annotatedString
        val argLinks = argAnnotated.getLinkAnnotations(0, argAnnotated.length)
        assertEquals(1, argLinks.size)
        val argLink = argLinks[0].item as androidx.compose.ui.text.LinkAnnotation.Url
        assertEquals("https://bgm.tv", argLink.url)

        // URL as content
        val contentResult = BBCodeParser.parse("[url]https://bgm.tv[/url]", "bangumi.tv", Color.Red)
        val contentAnnotated = (contentResult.first() as CommentBlock.Text).annotatedString
        val contentLinks = contentAnnotated.getLinkAnnotations(0, contentAnnotated.length)
        assertEquals(1, contentLinks.size)
        val contentLink = contentLinks[0].item as androidx.compose.ui.text.LinkAnnotation.Url
        assertEquals("https://bgm.tv", contentLink.url)

        // Invalid URL (does not start with http:// or https://)
        val invalidResult = BBCodeParser.parse("[url=ftp://invalid]FTP[/url]", "bangumi.tv", Color.Red)
        val invalidAnnotated = (invalidResult.first() as CommentBlock.Text).annotatedString
        val invalidLinks = invalidAnnotated.getLinkAnnotations(0, invalidAnnotated.length)
        assertEquals(0, invalidLinks.size)
    }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    @Test
    fun testClickableUserLinkAnnotation() {
        val result = BBCodeParser.parse("[user=12345]Username[/user]", "bangumi.tv", Color.Red)
        val annotated = (result.first() as CommentBlock.Text).annotatedString

        assertEquals("@Username", annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        val link = links[0].item as androidx.compose.ui.text.LinkAnnotation.Url
        assertEquals("https://bangumi.tv/user/12345", link.url)
    }

    @Test
    fun testMentionsFormatting() {
        val result = BBCodeParser.parse("Hello @user_name world", "bangumi.tv", Color.Blue)
        val annotatedString = (result.first() as CommentBlock.Text).annotatedString
        assertEquals("Hello @user_name world", annotatedString.text)

        // Mentions are highlighted with tertiaryColor and FontWeight.Medium
        assertEquals(1, annotatedString.spanStyles.size)
        val styleRange = annotatedString.spanStyles[0]
        assertEquals(6, styleRange.start)
        assertEquals(16, styleRange.end)
        assertEquals(Color.Blue, styleRange.item.color)
        assertEquals(FontWeight.Medium, styleRange.item.fontWeight)
    }

    @Test
    fun testSmileysAndInlineImages() {
        // Smiley resolution
        val smileyResult = BBCodeParser.parse("Hello (bgm01) World", "bangumi.tv", Color.Red)

        assertEquals(1, smileyResult.size)
        val smileyBlock = smileyResult[0] as CommentBlock.Text
        assertEquals("Hello (bgm01) World", smileyBlock.annotatedString.text)
        assertTrue(smileyBlock.inlineContent.containsKey("img_0"))
        assertEquals(20.sp, smileyBlock.inlineContent["img_0"]!!.placeholder.width)
        assertEquals(20.sp, smileyBlock.inlineContent["img_0"]!!.placeholder.height)

        val smileyAnnotations = smileyBlock.annotatedString.getStringAnnotations("androidx.compose.foundation.text.inlineContent", 6, 13)
        assertEquals(1, smileyAnnotations.size)
        assertEquals("img_0", smileyAnnotations[0].item)

        // Image tag resolution
        val imgResult = BBCodeParser.parse("Image: [img]https://example.com/pic.png[/img] end", "bangumi.tv", Color.Red)

        assertEquals(3, imgResult.size)

        val firstBlock = imgResult[0] as CommentBlock.Text
        assertEquals("Image: ", firstBlock.annotatedString.text)

        val secondBlock = imgResult[1] as CommentBlock.Image
        assertEquals("https://example.com/pic.png", secondBlock.url)

        val thirdBlock = imgResult[2] as CommentBlock.Text
        assertEquals(" end", thirdBlock.annotatedString.text)
    }

    @Test
    fun testMalformedAndNestedTags() {
        // Unclosed tags
        val unclosedResult = BBCodeParser.parse("[b]Unclosed Bold", "bangumi.tv", Color.Red)
        val unclosedAnnotated = (unclosedResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Unclosed Bold", unclosedAnnotated.text)
        assertEquals(1, unclosedAnnotated.spanStyles.size)
        val styleRange = unclosedAnnotated.spanStyles[0]
        assertEquals(0, styleRange.start)
        assertEquals(13, styleRange.end)
        assertEquals(FontWeight.Bold, styleRange.item.fontWeight)

        // Mismatched tags
        // When we have "[b]bold [i]italic[/b] text", the closing [/b] closes both [b] and [i].
        val mismatchedResult = BBCodeParser.parse("[b]bold [i]italic[/b] text", "bangumi.tv", Color.Red)
        val mismatchedAnnotated = (mismatchedResult.first() as CommentBlock.Text).annotatedString
        assertEquals("bold italic text", mismatchedAnnotated.text)

        // Two styles should be present: bold (0 until 11) and italic (5 until 11).
        assertEquals(2, mismatchedAnnotated.spanStyles.size)

        // Let's sort the styles by start to assert predictably
        val sortedStyles = mismatchedAnnotated.spanStyles.sortedBy { it.start }
        val bStyle = sortedStyles[0]
        val iStyle = sortedStyles[1]

        assertEquals(0, bStyle.start)
        assertEquals(11, bStyle.end)
        assertEquals(FontWeight.Bold, bStyle.item.fontWeight)

        assertEquals(5, iStyle.start)
        assertEquals(11, iStyle.end)
        assertTrue(FontStyle.Italic == iStyle.item.fontStyle)

        // Nested tags
        val nestedResult = BBCodeParser.parse("[b][i][u]Nested[/u][/i][/b]", "bangumi.tv", Color.Red)
        val nestedAnnotated = (nestedResult.first() as CommentBlock.Text).annotatedString
        assertEquals("Nested", nestedAnnotated.text)
        assertEquals(3, nestedAnnotated.spanStyles.size)

        for (style in nestedAnnotated.spanStyles) {
            assertEquals(0, style.start)
            assertEquals(6, style.end)
        }

        // Unclosed quote tag
        val unclosedQuoteResult = BBCodeParser.parse("[quote]unclosed", "bangumi.tv", Color.Red)
        assertTrue(unclosedQuoteResult.first() is CommentBlock.Quote)
        val unclosedQuoteBlock = unclosedQuoteResult.first() as CommentBlock.Quote
        assertEquals(1, unclosedQuoteBlock.blocks.size)
        val unclosedText = unclosedQuoteBlock.blocks.first() as CommentBlock.Text
        assertEquals("unclosed", unclosedText.annotatedString.text)

        // Nested quote tags
        val nestedQuoteResult = BBCodeParser.parse("[quote]outer [quote]inner[/quote] outer[/quote]", "bangumi.tv", Color.Red)
        assertTrue(nestedQuoteResult.first() is CommentBlock.Quote)
        val outerQuoteBlock = nestedQuoteResult.first() as CommentBlock.Quote
        assertEquals(3, outerQuoteBlock.blocks.size)

        val firstOuter = outerQuoteBlock.blocks[0] as CommentBlock.Text
        assertEquals("outer ", firstOuter.annotatedString.text)

        val innerQuoteBlock = outerQuoteBlock.blocks[1] as CommentBlock.Quote
        assertEquals(1, innerQuoteBlock.blocks.size)
        val innerText = innerQuoteBlock.blocks.first() as CommentBlock.Text
        assertEquals("inner", innerText.annotatedString.text)

        val secondOuter = outerQuoteBlock.blocks[2] as CommentBlock.Text
        assertEquals(" outer", secondOuter.annotatedString.text)
    }
}
