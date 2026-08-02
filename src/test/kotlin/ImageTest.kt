import io.github.onlaait.dcapi.article.ArticleRead
import kotlinx.coroutines.runBlocking
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.isDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test

class ImageTest {

    val gall = Secret.gall
    val articleId = Secret.articleId

    @Test
    fun main() = runBlocking {
        val dir = Path("download")
        if (!dir.isDirectory()) dir.createDirectory()
        ArticleRead(gall, articleId).get()!!.images.forEach {
            println(it)
            val bytes = it.download()
            dir.resolve(it.name).writeBytes(bytes)
        }
    }
}