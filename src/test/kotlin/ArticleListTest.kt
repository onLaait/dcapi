import com.github.onlaait.dcapi.article.ArticleList
import com.github.onlaait.dcapi.article.SearchType
import kotlin.test.Test

class ArticleListTest {

    val gall = Secret.gall

    @Test
    fun get() {
        ArticleList(gall).get()
            .also { it.forEach(::println);println(it.size) }
    }

    @Test
    fun search() {
        ArticleList(gall, searchKeyword = "", searchType = SearchType.COMMENT).get()
            .also { it.forEach(::println);println(it.size) }
    }
}