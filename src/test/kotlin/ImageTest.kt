import com.github.onlaait.dcapi.article.ArticleRead
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test

class ImageTest {

    val gall = Secret.gall
    val articleId = Secret.articleId

    @Test
    fun main() = runBlocking {
        val dir = Path("download")
        dir.createDirectories()
        ArticleRead(gall, articleId).get()!!.images.forEach {
            println(it)
            val writeChannel = dir.resolve(it.name).toFile().writeChannel()
            it.download().copyAndClose(writeChannel)
        }
    }
}