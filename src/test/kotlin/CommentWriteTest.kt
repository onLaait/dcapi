import com.github.onlaait.dcapi.comment.CommentWrite
import com.github.onlaait.dcapi.comment.DcconComment
import com.github.onlaait.dcapi.comment.TextComment
import com.github.onlaait.dcapi.dccon.DcconList
import com.github.onlaait.dcapi.session.LoginSession
import kotlin.test.Test

class CommentWriteTest {

    val gall = Secret.gall
    val articleId = Secret.articleId
    val commentId = 14048301
    val mentionCommentId = 14048351
    val text = TextComment("Hello, World!")
    val anonymousSession = Secret.anonymousSession
    val loginSession by lazy { Secret.loginSession }

    fun getCons(user: LoginSession? = null): List<DcconComment> {
        val cons = DcconList(user).get()[0].list
        return listOf(
            DcconComment(cons[0]),
            DcconComment(cons[0], cons[1])
        )
    }

    @Test
    fun `text anonymous write`() {
        CommentWrite(gall, articleId, text, anonymousSession).write()
            .also { println(it);require(it.success) }
    }

    @Test
    fun `text login write`() {
        CommentWrite(gall, articleId, text, loginSession).write()
            .also { println(it);require(it.success) }
    }

    @Test
    fun `text anonymous reply`() {
        CommentWrite(gall, articleId, text, anonymousSession).reply(commentId, mentionCommentId)
            .also { println(it);require(it.success) }
    }

    @Test
    fun `text login reply`() {
        CommentWrite(gall, articleId, text, loginSession).reply(commentId, mentionCommentId)
            .also { println(it);require(it.success) }
    }

    @Test
    fun `con anonymous write`() {
        getCons().forEach {
            CommentWrite(gall, articleId, it, anonymousSession).write()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun `con login write`() {
        getCons(loginSession).forEach {
            CommentWrite(gall, articleId, it, loginSession).write()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun `con anonymous reply`() {
        getCons().forEach {
            CommentWrite(gall, articleId, it, anonymousSession).reply(commentId, mentionCommentId)
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun `con login reply`() {
        getCons(loginSession).forEach {
            CommentWrite(gall, articleId, it, loginSession).reply(commentId, mentionCommentId)
                .also { println(it);require(it.success) }
        }
    }
}