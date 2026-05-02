import com.github.onlaait.dcapi.dccon.DcconBuy
import kotlin.test.Test

class DcconBuyTest {

    val packageId = 88128
    val loginSession by lazy { Secret.loginSession }

    @Test
    fun main() {
        require(loginSession.checkSession())
        DcconBuy(packageId, loginSession).buy()
            .also { println(it);require(it) }
    }
}