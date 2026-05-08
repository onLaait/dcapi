package com.github.onlaait.dcapi.dccon

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.session.LoginSession
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import com.github.onlaait.httputil.HttpUtils.defaultRetryConfig
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging

class DcconList(val session: LoginSession? = null, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun get(): List<DcconPackage> {
        logger.debug { "$this.get()" }
        return runBlocking { _get() }
    }

    private suspend fun _get(): List<DcconPackage> {
        val list = mutableListOf<DcconPackage>()
        var page = 0
        var maxPage = 0
        Utils.client(maxTries) {
            if (session != null) cookiesStorage(session.cookies)
        }.use { client ->
            while (page <= maxPage) {
                val form = parameters {
                    append("target", "icon")
                    append("page", page)
                }
                val res = client.submitForm("https://gall.dcinside.com/dccon/lists", form) {
                    headers {
                        set(HttpHeaders.Origin, "https://gall.dcinside.com")
                        set(HttpHeaders.Referrer, "https://gall.dcinside.com/")
                        xMLHttpRequest()
                    }
                    retry {
                        defaultRetryConfig(maxTries.let { if (it >= 1) it - 1 else Int.MAX_VALUE })
                        retryIf { _, res ->
                            val o = Json.parseToJsonElement(runBlocking { res.bodyAsText() })
                                .jsonObject
                            !o.containsKey("max_page")
                        }
                    }
                }
                val body = res.bodyAsText()

                try {
                    val o = Json.parseToJsonElement(body)
                        .jsonObject
                    if (o["target"]!!.jsonPrimitive.content == "shop") return emptyList()
                    maxPage = o["max_page"]!!.jsonPrimitive.int
                    o["list"]!!.jsonArray.forEach {
                        val o = it.jsonObject
                        val packageId = o["package_idx"]!!.jsonPrimitive.int
                        list += DcconPackage(
                            packageId = packageId,
                            title = o["title"]!!.jsonPrimitive.contentOrNull!!,
                            list = o["detail"]!!.jsonArray.map {
                                val o = it.jsonObject
                                Dccon(
                                    packageId = packageId,
                                    detailId = o["detail_idx"]!!.jsonPrimitive.int,
                                    code = Url(o["list_img"]!!.jsonPrimitive.contentOrNull!!).parameters["no"]!!,
                                    title = o["title"]!!.jsonPrimitive.contentOrNull!!
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    throw InvalidResponseException("디시콘 목록 응답", res.status, body, e)
                }
                page++
            }
        }
        return list
    }

    override fun toString() = "DcconList(session=$session)"
}
