import kotlin.test.Test

class LoginTest {

    @Test
    fun main() {
        val loginSession = Secret.loginSession
        println(loginSession)
        loginSession.cookies.forEach {
            println(it)
        }
        Thread.sleep(1000 * 60 * 60 * 6)
        require(loginSession.checkSession())
    }
}