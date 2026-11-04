package cn.xfangfang.biliduo.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentReactionTest {

    @Test
    fun like_from_neutral_increments_and_activates() {
        val next = toggleLikeReaction(liked = false, disliked = false, likeDelta = 0)
        assertTrue(next.liked)
        assertFalse(next.disliked)
        assertEquals(1, next.likeDelta)
    }

    @Test
    fun unlike_restores_neutral_count() {
        val next = toggleLikeReaction(liked = true, disliked = false, likeDelta = 1)
        assertFalse(next.liked)
        assertEquals(0, next.likeDelta)
    }

    @Test
    fun like_clears_existing_dislike() {
        val next = toggleLikeReaction(liked = false, disliked = true, likeDelta = 0)
        assertTrue(next.liked)
        assertFalse(next.disliked)
        assertEquals(1, next.likeDelta)
    }

    @Test
    fun dislike_clears_existing_like_and_decrements_count() {
        val next = toggleDislikeReaction(liked = true, disliked = false, likeDelta = 1)
        assertFalse(next.liked)
        assertTrue(next.disliked)
        assertEquals(0, next.likeDelta)
    }

    @Test
    fun un_dislike_keeps_like_state() {
        val next = toggleDislikeReaction(liked = false, disliked = true, likeDelta = 0)
        assertFalse(next.liked)
        assertFalse(next.disliked)
        assertEquals(0, next.likeDelta)
    }

    @Test
    fun dislike_does_not_touch_unrelated_like_delta() {
        val next = toggleDislikeReaction(liked = false, disliked = false, likeDelta = 2)
        assertTrue(next.disliked)
        assertEquals(2, next.likeDelta)
    }
}
