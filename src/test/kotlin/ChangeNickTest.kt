import com.github.onlaait.dcapi.session.ChangeNick
import kotlin.test.Test

class ChangeNickTest {

    val session = Secret.loginSession

    @Test
    fun main() {
        ChangeNick(session).change("abc123", ChangeNick.NickType.NON_FIXED)
            .also { println(it);require(it.success) }
    }
}