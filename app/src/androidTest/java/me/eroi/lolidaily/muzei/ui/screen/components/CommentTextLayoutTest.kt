package me.eroi.lolidaily.muzei.ui.screen.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.eroi.lolidaily.muzei.util.BBCodeParser
import me.eroi.lolidaily.muzei.util.CommentBlock
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentTextLayoutTest {
    @Test
    fun smileysStayInsideTheirLines() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (fontScale in listOf(0.85f, 1f, 1.5f, 2f)) {
            val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f, fontScale), LayoutDirection.Ltr)
            for (code in listOf("bgm01", "musume_07", "blake_19")) {
                for (content in listOf(
                    "前文($code)后文\n下一行",
                    "上一行\n前文($code)后文\n下一行",
                    "上一行\n前文($code)后文",
                    "搜了一下叫 タンザ，但是完全不记得是在什么地方出现过了（找到百科也看不懂）($code)\npixiv百科\n日文维基百科",
                    "[mask]上一行\n前文($code)后文\n下一行[/mask]",
                )) {
                    val block = BBCodeParser.parse(content, "bgm.tv", Color.Blue).single() as CommentBlock.Text
                    val annotations =
                        block.annotatedString.getStringAnnotations(
                            "androidx.compose.foundation.text.inlineContent",
                            0,
                            block.annotatedString.length,
                        )
                    val layout =
                        measurer.measure(
                            text = block.annotatedString,
                            style = commentTextStyle(TextStyle(fontSize = 14.sp), 20.sp, block.inlineContent.isNotEmpty()),
                            placeholders =
                                annotations.map {
                                    AnnotatedString.Range(block.inlineContent.getValue(it.item).placeholder, it.start, it.end)
                                },
                            constraints = Constraints(maxWidth = 240),
                        )
                    for ((index, annotation) in annotations.withIndex()) {
                        val rect = requireNotNull(layout.placeholderRects[index])
                        val line = layout.getLineForOffset(annotation.start)
                        val message = "$code at font scale $fontScale in $content: $rect, line $line"
                        assertTrue(message, rect.top >= layout.getLineTop(line) - 1f)
                        assertTrue(message, rect.bottom <= layout.getLineBottom(line) + 1f)
                        assertTrue(message, rect.left >= 0f && rect.right <= layout.size.width + 1f)
                    }
                }
            }
        }
    }
}
