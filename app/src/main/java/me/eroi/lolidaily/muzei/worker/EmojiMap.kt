package me.eroi.lolidaily.muzei.worker

import me.eroi.lolidaily.muzei.R

object EmojiMap {
    /** Map of reaction emoji value → drawable resource ID. */
    fun emojiResId(value: Int): Int? =
        when (value) {
            0 -> R.drawable.reaction_44
            104 -> R.drawable.reaction_65
            54 -> R.drawable.reaction_15
            140 -> R.drawable.reaction_101
            122 -> R.drawable.reaction_83
            90 -> R.drawable.reaction_51
            88 -> R.drawable.reaction_49
            80 -> R.drawable.reaction_41
            else -> null
        }

    @Deprecated("Use emojiResId() with local drawable resources instead")
    val EMOJI_URL_MAP =
        mapOf(
            0 to "https://bgm.tv/img/smiles/tv/44.gif",
            104 to "https://bgm.tv/img/smiles/tv/65.gif",
            54 to "https://bgm.tv/img/smiles/tv/15.gif",
            140 to "https://bgm.tv/img/smiles/tv/101.gif",
            122 to "https://bgm.tv/img/smiles/tv/83.gif",
            90 to "https://bgm.tv/img/smiles/tv/51.gif",
            88 to "https://bgm.tv/img/smiles/tv/49.gif",
            80 to "https://bgm.tv/img/smiles/tv/41.gif",
        )
}
