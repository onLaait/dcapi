import com.github.onlaait.dcapi.comment.CommentDelete
import kotlin.test.Test

class CommentDeleteTest {

    val gall = Secret.gall
    val articleId = Secret.articleId
    val commentId = 8229952
    val anonymousSession = Secret.anonymousSession
    val loginSession by lazy { Secret.loginSession }

    @Test
    fun anonymous() {
        CommentDelete(gall, articleId, commentId, anonymousSession).delete()
            .also { println(it);require(it.success) }
    }

    @Test
    fun login() {
        CommentDelete(gall, articleId, commentId, loginSession).delete()
            .also { println(it);require(it.success) }
    }
}