package me.eroi.lolidaily.muzei.ui.screen.components

/**
 * Detects Bangumi smiley text codes (e.g. `(bgm38)`, `(musume_07)`, `(blake_19)`) and
 * BBCode `[img]` markers in comment text, resolves them to image URLs, and produces a
 * cleaned string with `\uFFFC` placeholders suitable for Compose `InlineTextContent`.
 */
object SmileyMapper {
    // ── Regex patterns ──────────────────────────────────────────────

    /** Matches all known Bangumi smiley text codes. */
    val SMILEY_REGEX = Regex("""\((bgm\d+|musume_\d+|blake_\d+)\)""")

    /** Matches the `«img:url»` marker produced by [stripBbCode]. */
    private val IMG_MARKER_REGEX = Regex("""\u00abimg:(.*?)\u00bb""")

    /** Matches `@mentions` in text. */
    private val MENTION_REGEX = Regex("""@[\w\u4e00-\u9fff\u3400-\u4dbf]+""")

    // ── Data types ──────────────────────────────────────────────────

    data class SmileyCategory(
        val name: String,
        val iconCode: String,
        val isPixelArt: Boolean,
        val codes: List<String>,
    )

    data class InlineImage(
        val url: String,
        /** Index of the `\uFFFC` placeholder in the cleaned text. */
        val placeholderIndex: Int,
        /** `true` for pixel-art rendering (bgm / bgm_tv / bgm_vs / bgm_tv_500). */
        val isPixelArt: Boolean,
    )

    data class ParseResult(
        /** Cleaned text with `\uFFFC` where images should appear. */
        val text: String,
        /** Ordered list of inline images matching placeholder positions. */
        val images: List<InlineImage>,
        /** Ranges of `@mention` annotations in [text]. */
        val mentionRanges: List<IntRange>,
    )

    // ── Picker data ─────────────────────────────────────────────────

    private val musumeWebOrder =
        listOf(
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
            37, 38, 39, 40, 41, 42, 100, 106, 108, 118, 43, 44, 45, 46,
            47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61,
            62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76,
            101, 102, 103, 99, 107, 112, 109, 110, 111, 113, 114, 115,
            116, 117, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88,
            89, 90, 91, 92, 93, 104, 105, 94, 95, 96, 1, 2, 3, 4, 5,
        )

    private val blakeWebOrder =
        listOf(
            6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
            37, 38, 39, 40, 41, 42, 100, 106, 108, 118, 43, 44, 45, 46,
            47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61,
            62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76,
            101, 102, 103, 99, 107, 112, 109, 110, 111, 113, 114, 115,
            116, 117, 97, 98, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86,
            87, 88, 89, 90, 91, 92, 93, 104, 105, 94, 95, 96, 1, 2, 3,
            4, 5,
        )

    val categories =
        listOf(
            SmileyCategory(
                name = "Bgm",
                iconCode = "bgm01",
                isPixelArt = true,
                codes = (1..23).map { "bgm${it.pad2()}" },
            ),
            SmileyCategory(
                name = "Tv",
                iconCode = "bgm24",
                isPixelArt = true,
                codes = (24..125).map { "bgm$it" },
            ),
            SmileyCategory(
                name = "Musume",
                iconCode = "musume_01",
                isPixelArt = false,
                codes = musumeWebOrder.map { "musume_${it.pad2()}" },
            ),
            SmileyCategory(
                name = "Blake",
                iconCode = "blake_01",
                isPixelArt = false,
                codes = blakeWebOrder.map { "blake_${it.pad2()}" },
            ),
        )

    fun textCode(code: String): String = "($code)"

    fun resolveUrl(
        code: String,
        bgmDomain: String,
    ): String? = resolve(code, bgmDomain)?.first

    // ── URL resolution ──────────────────────────────────────────────

    /**
     * Resolves a smiley code to a full image URL.
     *
     * @param code  The inner code, e.g. `"bgm38"`, `"musume_07"`, `"blake_19"`.
     * @param bgmDomain  The user's configured Bangumi domain (e.g. `"chii.in"`).
     * @return `(fullUrl, isPixelArt)` or `null` if the code is unknown.
     */
    fun resolve(
        code: String,
        bgmDomain: String,
    ): Pair<String, Boolean>? {
        val domain = bgmDomain.ifBlank { "chii.in" }
        if (code.startsWith("musume_")) {
            val id = code.removePrefix("musume_").toIntOrNull() ?: return null
            if (id !in 1..118) return null
            return "https://$domain/img/smiles/musume/musume_${id.pad2()}.gif" to false
        }
        if (code.startsWith("blake_")) {
            val id = code.removePrefix("blake_").toIntOrNull() ?: return null
            if (id !in 1..118) return null
            return "https://$domain/img/smiles/blake/blake_${id.pad2()}.gif" to false
        }
        if (code.startsWith("bgm")) {
            val n = code.removePrefix("bgm").toIntOrNull() ?: return null
            return when (n) {
                11, 23 -> "https://$domain/img/smiles/bgm/${n.pad2()}.gif" to true
                in 1..22 -> "https://$domain/img/smiles/bgm/${n.pad2()}.png" to true
                in 24..125 -> "https://$domain/img/smiles/tv/${(n - 23).pad2()}.gif" to true
                in 200..238 -> "https://$domain/img/smiles/tv_vs/bgm_$n.png" to true
                in 500..529 -> "https://$domain/img/smiles/tv_500/bgm_$n.png" to true
                else -> null
            }
        }
        return null
    }

    // ── Text parsing ────────────────────────────────────────────────

    /**
     * Scans [text] (already BBCode-stripped) for smiley codes and `«img:url»` markers,
     * replaces them with `\uFFFC` placeholders, and returns the result.
     */
    fun parseInlineImages(
        text: String,
        bgmDomain: String,
    ): ParseResult {
        data class Replacement(
            val start: Int,
            val end: Int,
            val url: String,
            val isPixelArt: Boolean,
        )

        val replacements = mutableListOf<Replacement>()

        // 1) «img:url» markers (from BBCode [img] tags)
        for (match in IMG_MARKER_REGEX.findAll(text)) {
            val url = match.groupValues[1].trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                replacements += Replacement(match.range.first, match.range.last + 1, url, false)
            }
        }

        // 2) Smiley codes
        for (match in SMILEY_REGEX.findAll(text)) {
            val code = match.groupValues[1]
            val (url, isPixelArt) = resolve(code, bgmDomain) ?: continue
            replacements += Replacement(match.range.first, match.range.last + 1, url, isPixelArt)
        }

        // 3) Sort by position, build cleaned text with placeholders
        val sorted = replacements.sortedBy { it.start }
        val images = mutableListOf<InlineImage>()
        val sb = StringBuilder()
        var cursor = 0
        for (r in sorted) {
            if (r.start < cursor) continue // skip overlapping
            sb.append(text.substring(cursor, r.start))
            val placeholderIndex = sb.length
            sb.append('\uFFFC')
            images += InlineImage(r.url, placeholderIndex, r.isPixelArt)
            cursor = r.end
        }
        sb.append(text.substring(cursor))

        val cleanedText = sb.toString()

        // 4) Find @mention positions in cleaned text
        val mentions = MENTION_REGEX.findAll(cleanedText).map { it.range }.toList()

        return ParseResult(cleanedText, images, mentions)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun Int.pad2() = toString().padStart(2, '0')
}
