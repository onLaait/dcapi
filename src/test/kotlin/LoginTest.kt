import kotlin.test.Test

class LoginTest {

    @Test
    fun main() {
        val session = Secret.loginSession
        println(session)
        session.cookies.forEach {
            println(it)
        }
    }
}