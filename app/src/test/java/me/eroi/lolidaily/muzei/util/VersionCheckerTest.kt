package me.eroi.lolidaily.muzei.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCheckerTest {

    @Test
    fun testExtractReleaseNotesFromHtml_successfulExtraction() {
        // Test h1-h6 headers mapping to corresponding Markdown header levels
        val h1Html = "<div class=\"markdown-body\"><h1>Header 1</h1></div>"
        assertEquals("# Header 1", VersionChecker.extractReleaseNotesFromHtml(h1Html))

        val h2Html = "<div class=\"markdown-body\"><h2>Header 2</h2></div>"
        assertEquals("## Header 2", VersionChecker.extractReleaseNotesFromHtml(h2Html))

        val h3Html = "<div class=\"markdown-body\"><h3>Header 3</h3></div>"
        assertEquals("### Header 3", VersionChecker.extractReleaseNotesFromHtml(h3Html))

        val h4Html = "<div class=\"markdown-body\"><h4>Header 4</h4></div>"
        assertEquals("#### Header 4", VersionChecker.extractReleaseNotesFromHtml(h4Html))

        val h5Html = "<div class=\"markdown-body\"><h5>Header 5</h5></div>"
        assertEquals("##### Header 5", VersionChecker.extractReleaseNotesFromHtml(h5Html))

        val h6Html = "<div class=\"markdown-body\"><h6>Header 6</h6></div>"
        assertEquals("###### Header 6", VersionChecker.extractReleaseNotesFromHtml(h6Html))

        // Test lists (li)
        val listHtml = """
            <div class="markdown-body">
                <ul>
                    <li>Item 1</li>
                    <li>Item 2</li>
                </ul>
            </div>
        """.trimIndent()
        val expectedList = "- Item 1\n\n- Item 2"
        assertEquals(expectedList, VersionChecker.extractReleaseNotesFromHtml(listHtml))

        // Test single-line lists (no blank lines between items)
        val singleLineListHtml = "<div class=\"markdown-body\"><ul><li>Item 1</li><li>Item 2</li></ul></div>"
        assertEquals("- Item 1\n- Item 2", VersionChecker.extractReleaseNotesFromHtml(singleLineListHtml))
        // Test paragraphs (p)
        val pHtml = "<div class=\"markdown-body\"><p>Paragraph text</p></div>"
        assertEquals("Paragraph text", VersionChecker.extractReleaseNotesFromHtml(pHtml))

        // Test code blocks (code)
        val codeHtml = "<div class=\"markdown-body\"><code>val x = 1</code></div>"
        assertEquals("`val x = 1`", VersionChecker.extractReleaseNotesFromHtml(codeHtml))

        // Test bold/strong (strong)
        val strongHtml = "<div class=\"markdown-body\"><strong>important</strong></div>"
        assertEquals("**important**", VersionChecker.extractReleaseNotesFromHtml(strongHtml))

        // Test links (a)
        val aHtml = "<div class=\"markdown-body\"><a href=\"https://github.com\">GitHub</a></div>"
        assertEquals("[GitHub](https://github.com)", VersionChecker.extractReleaseNotesFromHtml(aHtml))

        // Test combinations
        val combinedHtml = """
            <div class="markdown-body">
                <h1>Release Notes</h1>
                <p>Welcome to <strong>version 1.0</strong>! This version contains some <code>code changes</code>.</p>
                <ul>
                    <li>First bug fix</li>
                    <li>Second feature request: check <a href="https://example.com">here</a></li>
                </ul>
            </div>
        """.trimIndent()
        val expectedCombined = """
            # Release Notes

            Welcome to **version 1.0**! This version contains some `code changes`.

            - First bug fix

            - Second feature request: check [here](https://example.com)
        """.trimIndent()
        assertEquals(expectedCombined, VersionChecker.extractReleaseNotesFromHtml(combinedHtml))
    }

    @Test
    fun testExtractReleaseNotesFromHtml_htmlEntityDecoding() {
        val html = """
            <div class="markdown-body">
                &amp; &lt; &gt; &quot; &#39; &apos; line1&#10;line2
            </div>
        """.trimIndent()
        val expected = "& < > \" ' ' line1\nline2"
        assertEquals(expected, VersionChecker.extractReleaseNotesFromHtml(html))
    }

    @Test
    fun testExtractReleaseNotesFromHtml_noMarkdownBodyClass() {
        val noClassHtml = "<div>No markdown-body class here</div>"
        assertNull(VersionChecker.extractReleaseNotesFromHtml(noClassHtml))

        val wrongClassHtml = "<div class=\"other-class\">No markdown-body class here</div>"
        assertNull(VersionChecker.extractReleaseNotesFromHtml(wrongClassHtml))
    }

    @Test
    fun testExtractReleaseNotesFromHtml_emptyContent() {
        val emptyHtml = "<div class=\"markdown-body\"></div>"
        assertNull(VersionChecker.extractReleaseNotesFromHtml(emptyHtml))

        val spacesHtml = "<div class=\"markdown-body\">   </div>"
        assertNull(VersionChecker.extractReleaseNotesFromHtml(spacesHtml))

        val emptyTagsHtml = "<div class=\"markdown-body\"><p></p></div>"
        assertNull(VersionChecker.extractReleaseNotesFromHtml(emptyTagsHtml))
    }

    @Test
    fun testExtractReleaseNotesFromHtml_multiLineCleanup() {
        val html = """
            <div class="markdown-body">


                Line 1


                Line 2


            </div>
        """.trimIndent()
        val expected = "Line 1\n\nLine 2"
        assertEquals(expected, VersionChecker.extractReleaseNotesFromHtml(html))
    }
}
