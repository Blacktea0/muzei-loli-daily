package me.eroi.lolidaily.muzei.ui.screen.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionComponentsTest {
    @Test
    fun firstCommentReactionTriggersWhenPreviouslyEmpty() {
        assertEquals(104, findNewlySelectedReactionValue(setOf(104), emptySet()))
    }

    @Test
    fun unchangedSelectionDoesNotTriggerAgain() {
        assertNull(findNewlySelectedReactionValue(setOf(104), setOf(104)))
    }

    @Test
    fun switchingReactionTriggersNewValue() {
        assertEquals(54, findNewlySelectedReactionValue(setOf(54), setOf(104)))
    }

    @Test
    fun removingReactionDoesNotTriggerExplosion() {
        assertNull(findNewlySelectedReactionValue(emptySet(), setOf(104)))
    }
}
