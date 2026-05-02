package com.github.onlaait.dcapi.dccon

import com.github.onlaait.dcapi.Dcapi
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
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging

class DcconInfo(val session: LoginSession? = null, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun getFromPackageId(packageId: Int): Result? {
        logger.debug { "DcconInfo.getFromPackageId(packageId=$packageId)" }
        return runBlocking { _getFromPackageId(packageId) }
    }

    private suspend fun _getFromPackageId(packageId: Int): Result? {
        val form = parameters {
            append("ci_t", Utils.generateRandomCiC())
            append("package_idx", packageId)
            append("code", "")
        }
        val body = Utils.client(maxTries) {
            if (session != null) cookiesStorage(session.cookies)
        }.use { client ->
            val res = client.submitForm("https://dccon.dcinside.com/index/package_detail", form) {
                headers {
                    set(HttpHeaders.Origin, "https://dccon.dcinside.com")
                    set(HttpHeaders.Referrer, "https://dccon.dcinside.com/")
                    xMLHttpRequest()
                }
            }
            res.bodyAsText()
        }
        if (!body.startsWith('{')) return null
        return parse(body)
    }

    fun getFromCode(code: String): Result? = runBlocking {
        logger.debug { "DcconInfo.getFromCode(code=$code)" }
        _getFromCode(code)
    }

    private suspend fun _getFromCode(code: String): Result? {
        val form = parameters {
            append("ci_t", Utils.generateRandomCiC())
            append("package_idx", "")
            append("code", code)
        }
        val body = Utils.client(maxTries) {
            if (session != null) cookiesStorage(session.cookies)
        }.use { client ->
            val res = client.submitForm("https://gall.dcinside.com/dccon/package_detail", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, "https://gall.dcinside.com/")
                    xMLHttpRequest()
                }
            }
            res.bodyAsText()
        }
        if (!body.startsWith('{')) return null
        return parse(body)
    }

    private fun parse(s: String): Result {
        val o = Json.parseToJsonElement(s).jsonObject
        val info = o["info"]!!.jsonObject
        val packageId = info["package_idx"]!!.jsonPrimitive.int
        return Result(
            pkg = DcconPackage(
                packageId = packageId,
                title = info["title"]!!.jsonPrimitive.contentOrNull!!,
                list = o["detail"]!!.jsonArray.map {
                    val o = it.jsonObject
                    Dccon(
                        packageId = packageId,
                        detailId = o["idx"]!!.jsonPrimitive.int,
                        code = o["path"]!!.jsonPrimitive.contentOrNull!!,
                        title = o["title"]!!.jsonPrimitive.contentOrNull!!
                    )
                }
            ),
            bought = info["buy_idx"]?.jsonPrimitive?.contentOrNull != null
        )
    }

    data class Result(
        val pkg: DcconPackage,
        val bought: Boolean
    )
}