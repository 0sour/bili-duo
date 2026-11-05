package cn.xfangfang.biliduo.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentComposeTest {

    @Test
    fun blankCommentIsRejected() {
        assertEquals("评论不能为空", validateComment(""))
        assertEquals("评论不能为空", validateComment("   \n\t "))
    }

    @Test
    fun normalCommentPasses() {
        assertNull(validateComment("测试评论"))
        assertNull(validateComment(" 前后空格会被 trim  "))
    }

    @Test
    fun overLimitCommentIsRejected() {
        assertEquals(
            "评论最多 $COMMENT_MAX_LENGTH 字",
            validateComment("x".repeat(COMMENT_MAX_LENGTH + 1)),
        )
    }
}
