package me.eroi.lolidaily.muzei.worker

import me.eroi.lolidaily.muzei.R

object EmojiMap {
    /** Map of reaction emoji value → drawable resource ID. */
    fun emojiResId(value: Int): Int? =
        when (value) {
            0 -> R.drawable.reaction_44
            79 -> R.drawable.reaction_40
            54 -> R.drawable.reaction_15
            140 -> R.drawable.reaction_101
            62 -> R.drawable.reaction_23
            122 -> R.drawable.reaction_83
            104 -> R.drawable.reaction_65
            80 -> R.drawable.reaction_41
            141 -> R.drawable.reaction_102
            88 -> R.drawable.reaction_49
            85 -> R.drawable.reaction_46
            90 -> R.drawable.reaction_51
            else -> null
        }
}
