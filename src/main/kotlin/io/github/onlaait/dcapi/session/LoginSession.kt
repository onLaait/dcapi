package io.github.onlaait.dcapi.session

import io.github.onlaait.dcapi.exception.InvalidResponseException
import io.github.onlaait.dcapi.util.Utils
import io.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import io.github.onlaait.httputil.HttpUtils.cookiesStorage
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = LoginSessionAsStringSerializer::class)
@ConsistentCopyVisibility
data class LoginSession private constructor(
    val id: String,
    override val password: String
) : Session {

    companion object {
        private val ALERT_RGX = Regex("alert\\([\"'](?<t>.+)[\"']\\)")

        private val MAX_SESSION_TIME = 6.hours.inWholeMilliseconds

        fun login(id: String, password: String): LoginSession = LoginSession(id, password).apply { login() }
    }

    lateinit var name: String
        internal set

    lateinit var cookies: List<Cookie>
        private set

    private var loginTime = 0L

    private fun login() = runBlocking { _login() }

    private suspend fun _login() {
        Utils.client {
            cookiesStorage()
        }.use { client ->
            val doc: Document
            val ciC: String
            run {
                val res = client.getLoginBox()
                val body = res.bodyAsText()
                doc = Jsoup.parseBodyFragment(body)
                ciC = res.setCookie().first { it.name == "ci_c" }.value
            }

            val form = Utils.serialize(doc.body().getElementById("login_process")!!) + parameters {
                append("ci_t", ciC)
                append("user_id", id)
                append("pw", password)
                append("id_cookie", "on")
            }
            val res = client.submitForm("https://sign.dcinside.com/login/member_check", form) {
                headers {
                    set(HttpHeaders.Origin, "https://www.dcinside.com")
                    set(HttpHeaders.Referrer, "https://www.dcinside.com/")
                }
            }
            val body = res.bodyAsText()
            val mat = ALERT_RGX.find(body)
            if (mat != null) {
                val alertTxt = mat.groups["t"]!!.value
                throw InvalidResponseException("로그인 응답", res.status, alertTxt)
            }

            loginTime = System.currentTimeMillis()
            cookies = client.cookies("https://www.dcinside.com/")
        }
        require(checkSession())
    }

    fun checkSession(): Boolean = runBlocking { _checkSession() }

    private suspend fun _checkSession(): Boolean {
        val res = Utils.client {
            cookiesStorage(cookies)
        }.use { client ->
            client.getLoginBox()
        }
        val body = res.bodyAsText()
        try {
            val doc = Jsoup.parseBodyFragment(body)
            val loginBox = doc.body().getElementById("login_box") ?: return false
            name = loginBox.selectFirst(".user_name")!!.text().removeSuffix("님")
            return true
        } catch (e: Exception) {
            throw InvalidResponseException("로그인 후 로그인 박스 응답", res.status, body, e)
        }
    }

    private suspend fun HttpClient.getLoginBox(): HttpResponse =
        get("https://www.dcinside.com/main/login_box") {
            headers {
                set(HttpHeaders.Host, "www.dcinside.com")
                set(HttpHeaders.Referrer, "https://www.dcinside.com/")
                xMLHttpRequest()
            }
        }


    fun ensureLogined() {
        if (System.currentTimeMillis() - loginTime > MAX_SESSION_TIME || !checkSession()) login()
    }

    override fun toString() = "LoginSession($id)"
}
