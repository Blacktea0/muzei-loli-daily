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
            85 -> R.drawable.reaction_46
            else -> null
        }
}
