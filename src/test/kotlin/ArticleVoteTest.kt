import com.github.onlaait.dcapi.article.ArticleVote
import kotlin.test.Test

class ArticleVoteTest {

    val gall = Secret.gall
    val articleId = Secret.articleId
    val loginSession by lazy { Secret.loginSession }

    @Test
    fun anonymous() {
        ArticleVote(gall, articleId).run {
            upvote()
                .also { println(it);require(it.success) }
            downvote()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun login() {
        ArticleVote(gall, articleId, loginSession).run {
            upvote()
                .also { println(it);require(it.success) }
            downvote()
                .also { println(it);require(it.success) }
        }
    }
}