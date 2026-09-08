package me.eroi.lolidaily.muzei.ui.screen.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterUrlDialogTest {
    @Test
    fun acceptsCharacterLinksAcrossBangumiDomains() {
        for (domain in listOf("bgm.tv", "bangumi.tv", "chii.in")) {
            assertEquals(123, parseBangumiCharacterId("https://$domain/character/123"))
            assertEquals(123, parseBangumiCharacterId("http://$domain/character/123"))
        }
    }

    @Test
    fun acceptsPastedLinksWithWhitespaceQueryAndFragment() {
        assertEquals(123, parseBangumiCharacterId("  https://BGM.TV/character/123/?foo=bar#info\n"))
    }

    @Test
    fun acceptsPositiveCharacterIds() {
        assertEquals(123, parseBangumiCharacterId(" 123 "))
        assertEquals(123, parseBangumiCharacterId("00123"))
        assertEquals(Int.MAX_VALUE, parseBangumiCharacterId("2147483647"))
    }

    @Test
    fun parsesMixedInputsAndDeduplicatesInInputOrder() {
        assertEquals(
            listOf(123, 456, 789),
            parseBangumiCharacterIds(" 123 https://bgm.tv/character/456 00123 https://chii.in/character/123 789 "),
        )
    }

    @Test
    fun acceptsRepeatedSpacesNewlinesTabsAndFullWidthSpaces() {
        assertEquals(listOf(123, 456, 789, 12), parseBangumiCharacterIds("123  \n456\t789\u300012"))
    }

    @Test
    fun rejectsWholeBatchIfAnyEntryIsInvalid() {
        assertNull(parseBangumiCharacterIds("123 invalid 456"))
        assertNull(parseBangumiCharacterIds("123 0"))
        assertNull(parseBangumiCharacterIds(" \n\t "))
    }

    @Test
    fun rejectsInvalidLinksAndNonCharacterPages() {
        val invalidLinks = listOf(
            "",
            "0",
            "-1",
            "+123",
            "1.5",
            "2147483648",
            "123 456",
            "https://bgm.tv/subject/123",
            "https://bgm.tv/person/123",
            "https://bgm.tv/character/123/comments",
            "https://bgm.tv/character/",
            "https://bgm.tv/character/0",
            "https://bgm.tv/character/-1",
            "https://bgm.tv/character/2147483648",
            "https://bgm.tv/character/abc",
            "https://bgm.tv.example.com/character/123",
            "https://example.com/character/123",
            "https://bgm.tv@example.com/character/123",
            "https://user@bgm.tv/character/123",
            "ftp://bgm.tv/character/123",
        )
        invalidLinks.forEach { assertNull(it, parseBangumiCharacterId(it)) }
    }
}
