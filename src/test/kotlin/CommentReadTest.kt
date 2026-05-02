import com.github.onlaait.dcapi.comment.CommentRead
import kotlin.test.Test

class CommentReadTest {

    val gall = Secret.gall
    val articleId = Secret.articleId
    val commentId = 1338334775

    @Test
    fun main() {
        CommentRead(gall, articleId).get()
            .also { require(it != null);println("$it\n");it.comments.forEach(::println) }
    }

    @Test
    fun getFocused() {
        CommentRead(gall, articleId).getFocused(commentId)
            .also { require(it != null);println("$it\n");it.comments.forEach(::println) }
    }
}