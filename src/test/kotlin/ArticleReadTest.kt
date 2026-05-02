import com.github.onlaait.dcapi.article.ArticleRead
import kotlin.test.Test

class ArticleReadTest {

    val gall = Secret.gall
    val articleId = Secret.articleId

    @Test
    fun main() {
        ArticleRead(gall, articleId).get()
            .also { println(it);require(it != null) }
    }
}