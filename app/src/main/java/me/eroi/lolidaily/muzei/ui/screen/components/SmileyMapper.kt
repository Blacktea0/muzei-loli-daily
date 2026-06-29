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
        @Suppress("UNUSED_PARAMETER") bgmDomain: String,
    ): Pair<String, Boolean>? {
        if (code.startsWith("musume_")) {
            val id = code.removePrefix("musume_").toIntOrNull() ?: return null
            if (id !in 1..118) return null
            return "https://lain.bgm.tv/img/smiles/musume/musume_${id.pad2()}.gif" to false
        }
        if (code.startsWith("blake_")) {
            val id = code.removePrefix("blake_").toIntOrNull() ?: return null
            if (id !in 1..118) return null
            return "https://lain.bgm.tv/img/smiles/blake/blake_${id.pad2()}.gif" to false
        }
        if (code.startsWith("bgm")) {
            val n = code.removePrefix("bgm").toIntOrNull() ?: return null
            return when (n) {
                in 1..22 -> "https://lain.bgm.tv/img/smiles/bgm/${n.pad2()}.png" to true
                in 24..125 -> "https://lain.bgm.tv/img/smiles/tv/${n - 23}.gif" to true
                in 200..238 -> "https://lain.bgm.tv/img/smiles/tv_vs/bgm_$n.png" to true
                in 500..529 -> "https://lain.bgm.tv/img/smiles/tv_500/bgm_$n.png" to true
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
