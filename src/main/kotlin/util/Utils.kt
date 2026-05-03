package com.github.onlaait.dcapi.util

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.gall.GallType
import com.github.onlaait.httputil.HttpUtils
import com.github.onlaait.httputil.HttpUtils.defaultRetryConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId

internal object Utils {

    fun client(maxTries: Int = Dcapi.maxTries, block: HttpClientConfig<CIOEngineConfig>.() -> Unit = {}): HttpClient =
        HttpUtils.standardClient {
            install(HttpRequestRetry) {
                defaultRetryConfig(maxTries.let { if (it >= 1) it - 1 else Int.MAX_VALUE })
                retryIf { _, res -> res.status.value == 200 && runBlocking { res.bodyAsText() }.isBlank() }
            }
            defaultRequest {
                headers {
                    set(HttpHeaders.AcceptLanguage, HttpUtils.ACCEPT_LANGUAGE_KR)
                }
            }
            block(this)
        }

    fun mobileClient(maxTries: Int = Dcapi.maxTries, block: HttpClientConfig<CIOEngineConfig>.() -> Unit = {}): HttpClient =
        client(maxTries) {
            defaultRequest {
                headers {
                    set(HttpHeaders.UserAgent, HttpUtils.MOBILE_USER_AGENT)
                }
            }
            block(this)
        }

    fun HeadersBuilder.xMLHttpRequest() {
        set("x-requested-with", "XMLHttpRequest")
    }

    private val SERVICE_CODE_RGX = Regex("var _r = _d\\('(?<r>.+)'\\)")

    fun getServiceCode(doc: Document): String {
        val r = run {
            val scripts = doc.body()
                .select("script[type=\"text/javascript\"]")
            for (e in scripts) {
                val mat = SERVICE_CODE_RGX.find(e.data()) ?: continue
                return@run mat.groups["r"]!!.value
            }
            throw RuntimeException("r 값을 찾을 수 없음: $doc")
        }

        var _r = _d(r)

        fun rc1() {
            var tvl = _r
            var fi = tvl[0].digitToInt()
            fi = if (fi > 5) fi - 5 else fi + 4
            tvl = tvl.replaceFirstChar { fi.digitToChar() }
            _r = tvl
        }
        rc1()

        lateinit var serviceCode: String
        fun _f() {
            val r = doc.body()
                .selectFirst("input[name=\"service_code\"]")!!
                .`val`()
            val _rs = _r.split(',')
            val c = StringBuilder()
            for (n in 0 until _rs.size) {
                c.append((2 * (_rs[n].toDouble() - n - 1) / (13 - n - 1)).toInt().toChar())
            }
            serviceCode = r.replace(Regex(".{10}$"), c.toString())
        }
        _f()

        return serviceCode
    }

    private fun _d(r: String): String {
        val i = "yL/M=zNa0bcPQdReSfTgUhViWjXkYIZmnpo+qArOBslCt2D3uE4Fv5G6wH178xJ9K"
        val o = StringBuilder()
        var c = 0
        val r = r.replace(Regex("[^A-Za-z0-9+/=]"), "")
        while (c < r.length) {
            val t = i.indexOf(r[c++])
            val f = i.indexOf(r[c++])
            val d = i.indexOf(r[c++])
            val h = i.indexOf(r[c++])
            val a = (t shl 2) or (f shr 4)
            val e = (15 and f) shl 4 or (d shr 2)
            val n = (3 and d) shl 6 or h
            o.append(a.toChar())
            if (64 != d) o.append(e.toChar())
            if (64 != h) o.append(n.toChar())
        }
        return o.toString()
    }

    val CHARS = ('a'..'z') + ('0'..'9')

    fun generateRandomCiC(): String = CharArray(32) { CHARS.random() }.concatToString()

    fun consumeDoc(gall: Gall, doc: Document) {
        val e = doc.selectFirst("[name=\"gall_nick_name\"], [name=\"name\"]") ?: return
        val v = e.`val`()
        if (v.isEmpty()) return
        gall.nick = v
    }

    fun Gall.absoulteId(): String =
        when (type) {
            GallType.MAIN, GallType.MINOR -> id
            GallType.MINI, GallType.PERSON -> "${type.symbol.lowercase()}$$id"
        }

    suspend fun HttpClient.readArticle(url: String): Triple<HttpResponse, String, Document>? {
        val res = get(url)
        if (res.status.value == 404 || res.headers.contains("location")) return null
        val body = res.bodyAsText()
        val doc = Jsoup.parse(body)
        if (doc.body().childNodeSize() == 0) return null
        return Triple(res, body, doc)
    }

    val KR_ZONE = ZoneId.of("Asia/Seoul")

    fun generateBoundary(): String = buildString {
        append("----WebKitFormBoundary")
        val chs = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        repeat(16) {
            append(chs.random())
        }
    }

    fun serialize(element: Element): Parameters =
        parameters {
            for (e in element) {
                if (!e.nameIs("input")) continue
                val type = e.attribute("type")?.value
                if (type != null) {
                    when (type) {
                        "checkbox" -> if (e.attribute("checked").let { it == null || it.value == "false" }) continue
                        "submit", "reset", "button", "file" -> continue
                    }
                }
                val name = e.attr("name")
                if (name.isBlank()) continue
                append(name, e.`val`())
            }
        }
}