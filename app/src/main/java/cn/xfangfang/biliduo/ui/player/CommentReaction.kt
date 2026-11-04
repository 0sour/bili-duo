package cn.xfangfang.biliduo.ui.player

/**
 * Local optimistic state for a comment's like/dislike buttons.
 *
 * Bilibili treats the two actions as mutually exclusive: pressing one clears
 * the other server-side. We mirror that immediately so the UI never shows both
 * active, then roll back from the previous snapshot if the request fails.
 */
data class CommentReaction(
    val liked: Boolean,
    val disliked: Boolean,
    val likeDelta: Int,
)

fun toggleLikeReaction(
    liked: Boolean,
    disliked: Boolean,
    likeDelta: Int,
): CommentReaction {
    val nextLiked = !liked
    return CommentReaction(
        liked = nextLiked,
        disliked = if (nextLiked) false else disliked,
        likeDelta = likeDelta + if (nextLiked) 1 else -1,
    )
}

fun toggleDislikeReaction(
    liked: Boolean,
    disliked: Boolean,
    likeDelta: Int,
): CommentReaction {
    val nextDisliked = !disliked
    val clearsLike = nextDisliked && liked
    return CommentReaction(
        liked = if (clearsLike) false else liked,
        disliked = nextDisliked,
        likeDelta = if (clearsLike) likeDelta - 1 else likeDelta,
    )
}
