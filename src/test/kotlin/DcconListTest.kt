import com.github.onlaait.dcapi.dccon.DcconList
import kotlin.test.Test

class DcconListTest {

    val loginSession by lazy { Secret.loginSession }

    @Test
    fun anonymous() {
        DcconList().get()
            .also { it.forEach { println(it) } }
    }

    @Test
    fun login() {
        DcconList(loginSession).get()
            .also { it.forEach { println(it) } }
    }
}