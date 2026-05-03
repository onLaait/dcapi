import com.github.onlaait.dcapi.article.ArticleRead
import kotlin.test.Test

class ArticleReadTest {

    val gall = Secret.gall
    val articleId = Secret.articleId
    val loginSession by lazy { Secret.loginSession }

    @Test
    fun main() {
        ArticleRead(gall, articleId).get()
            .also { println(it);require(it != null) }
    }

    @Test
    fun adult() {
        ArticleRead(gall, articleId, loginSession).get()
            .also { println(it);require(it != null) }
    }
}