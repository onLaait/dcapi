package com.github.onlaait.dcapi.dccon

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.session.LoginSession
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup

class DcconBuy(val packageId: Int, val session: LoginSession, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun buy(): Boolean {
        logger.debug { "$this.buy()" }
        return runBlocking { _buy() }
    }

    private suspend fun _buy(): Boolean {
        val (status, body) = Utils.client(maxTries) {
            cookiesStorage(session.cookies)
        }.use { client ->
            val ciC = run {
                val res = client.get("https://dccon.dcinside.com/")
                res.setCookie().first { it.name == "ci_c" }.value
            }
            val form = parameters {
                append("package_idx", packageId)
                append("ci_t", ciC)
            }
            val res = client.submitForm("https://dccon.dcinside.com/index/buy", form) {
                headers {
                    set(HttpHeaders.Origin, "https://dccon.dcinside.com")
                    set(HttpHeaders.Referrer, "https://dccon.dcinside.com/")
                    xMLHttpRequest()
                }
            }
            res.status to res.bodyAsText()
        }
        try {
            if (body == "ok") return true
            require(body == "fail" || Jsoup.parse(body).body().text().isNotBlank())
            return false
        } catch (e: Exception) {
            throw InvalidResponseException("디시콘 구매 응답", status, body, e)
        }
    }

    override fun toString() = "DcconBuy(packageId=$packageId, session=$session)"
}