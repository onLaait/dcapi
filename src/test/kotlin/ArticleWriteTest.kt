import com.github.onlaait.dcapi.article.ArticleWrite
import kotlin.io.path.Path
import kotlin.test.Test

class ArticleWriteTest {

    val gall = Secret.gall
    val anonymousSession = Secret.anonymousSession
    val loginSession by lazy { Secret.loginSession }
    val image = Path("image.png")

    @Test
    fun anonymous() {
        ArticleWrite(gall, anonymousSession).run {
            subject = randomString()
            content = ""
            repeat(3) {
                val res = uploadImage(image)
                require(res.success) { res }
                content += res.makeJsoupNode().toString()
            }
            write()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun `anonymous image`() {
        ArticleWrite(gall, anonymousSession).run {
            subject = randomString()
            content = ""
            repeat(3) {
                val res = uploadImage(image)
                require(res.success) { res }
                content += res.makeJsoupNode().toString()
            }
            write()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun login() {
        ArticleWrite(gall, loginSession).run {
            subject = randomString()
            content = ""
            repeat(3) {
                val res = uploadImage(image)
                require(res.success) { res }
                content += res.makeJsoupNode().toString()
            }
            content += "<p>${randomString()}</p>"
            write()
                .also { println(it);require(it.success) }
        }
    }

    @Test
    fun `login image`() {
        ArticleWrite(gall, loginSession).run {
            subject = randomString()
            content = ""
            repeat(3) {
                val res = uploadImage(image)
                require(res.success) { res }
                content += res.makeJsoupNode().toString()
            }
            content += "<p>${randomString()}</p>"
            write()
                .also { println(it);require(it.success) }
        }
    }

    fun randomString(): String = buildString { repeat(10) { append(('가'..'힣').random()) } }
}