package com.github.onlaait.dcapi.article

import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.session.AnonymousSession
import com.github.onlaait.dcapi.session.LoginSession
import com.github.onlaait.dcapi.session.Session
import com.github.onlaait.dcapi.util.AdvancedCookiesStorage
import com.github.onlaait.dcapi.util.FailCause
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.addCookie
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ArticleWrite(val gall: Gall, val session: Session) : Logging, Closeable {

    private companion object {
        const val MAX_SIZE = 20 * 1024 * 1024
        val ALLOWED_EXTENSIONS = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        const val MAX_FILE_COUNT = 50
    }

    private val writeUrl = gall.writeUrl()

    private val cs = AdvancedCookiesStorage().apply {
        if (session is LoginSession) runBlocking {
            session.cookies.forEach { addCookie(it) }
        }
    }
    private val client = Utils.client {
        cookiesStorage(cs)
    }

    private var ready = false
    private var readyTime = 0L

    lateinit var doc: Document

    lateinit var subject: String
    lateinit var content: String

    private val fileNos = mutableListOf<Int>()

    fun ready() = runBlocking { _ready() }

    private suspend fun _ready() {
        if (ready) return
        doc = run {
            val res = client.get(writeUrl)
            readyTime = System.currentTimeMillis()
            val body = res.bodyAsText()
            Jsoup.parse(body)
        }
        ready = true
    }

    fun uploadImage(path: Path): ImageUploadResult {
        logger.debug { "$this.uploadImage(path=$path)" }
        return runBlocking { _uploadImage(path) }
    }

    private suspend fun _uploadImage(path: Path): ImageUploadResult {
        if (fileNos.size == MAX_FILE_COUNT) return ImageUploadResult(false, failCause = FailCause.MAX_FILE_COUNT_REACHED)
        if (path.fileSize() > MAX_SIZE) return ImageUploadResult(false, failCause = FailCause.FILE_SIZE_TOO_BIG)
        if (!ALLOWED_EXTENSIONS.contains(path.extension)) return ImageUploadResult(false, failCause = FailCause.FILE_EXTENSION_NOT_ALLOWED)
        ready()
        val res = client.post("https://upimg.dcinside.com/upimg_file.php?id=${gall.id}") {
            setBody(MultiPartFormDataContent(
                formData {
                    append("files[]", path.readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.defaultForPath(path).contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"${path.name}\"")
                    })
                    append("id", gall.id)
                    append("r_key", doc.getElementById("r_key")!!.`val`())
                    append("gall_no", doc.getElementById("gallery_no")!!.`val`())
                    append("_GALLTYPE_", doc.getElementById("_GALLTYPE_")!!.`val`())
                },
                Utils.generateBoundary()
            ))
            headers {
                set(HttpHeaders.Origin, "https://gall.dcinside.com")
                set(HttpHeaders.Referrer, writeUrl)
            }
        }
        val body = res.bodyAsText()
        try {
            val o = Json.parseToJsonElement(body)
                .jsonObject["files"]!!
                .jsonArray[0]
                .jsonObject
            val errorE = o["error"]
            if (errorE != null) return ImageUploadResult(false, failCause = errorE.jsonPrimitive.contentOrNull)
            val imageUrl = (o["web__url"] ?: o["web2__url"] ?: o["url"]!!).jsonPrimitive.contentOrNull!!
            val fileTempNo = o["file_temp_no"]!!.jsonPrimitive.int
            fileNos += fileTempNo
            return ImageUploadResult(
                true,
                src = imageUrl,
                dataTempNo = fileTempNo
            )
        } catch (e: Exception) {
            throw InvalidResponseException("이미지 업로드 응답", res.status, body, e)
        }
    }

    data class ImageUploadResult(
        val success: Boolean,
        val src: String = "",
        val dataTempNo: Int = 0,
        val failCause: String? = null
    ) {
        fun makeJsoupNode(): Element {
            require(success)
            return Element("img").apply {
                attr("src", src)
                attr("data-tempno", dataTempNo.toString())
            }
        }
    }

    fun write(): Result {
        logger.debug { "$this.write()" }
        return runBlocking { _write() }
    }

    private suspend fun _write(): Result {
        require(!content.contains('\n'))
        ready()
        val serial = Utils.serialize(doc.getElementById("write")!!)
        if (session is AnonymousSession && session.name == null && !serial.contains("gall_nick_name")) {
            return Result(false, failCause = FailCause.CANNOT_USE_GALL_NICK)
        }
        val ciC = client.cookies("https://gall.dcinside.com").first { it.name == "ci_c" }.value
        cs.addCookie(Cookie("_ga", "GA1.1.${Random.nextInt(100000000..999999999)}.${(Clock.System.now() - 5.minutes).epochSeconds}", domain = ".dcinside.com", path = "/"))

        val blockKey2 = run {
            val form = parameters {
                append("ci_t", ciC)
                append("block_key", doc.getElementById("block_key")!!.`val`())
            }

            val sleepTime = readyTime + 2000 - System.currentTimeMillis()
            if (sleepTime > 0) Thread.sleep(sleepTime)

            val res = client.submitForm("https://gall.dcinside.com/block/block/", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, writeUrl)
                    xMLHttpRequest()
                }
            }
            val body = res.bodyAsText()
            try {
                require(res.status.value == 200)
                body.trim()
            } catch (e: Exception) {
                throw InvalidResponseException("글쓰기 block 응답", res.status, body, e)
            }
        }

        val form = parameters {
            appendAll(serial)
            if (fileNos.isNotEmpty()) set("upload_status", "Y")
            if (session is AnonymousSession) {
                set("use_gall_nick", if (session.name == null) "Y" else "N")
                if (session.name != null) set("name", session.name)
                set("password", session.password)
            }
            set("subject", subject)
            set("memo", content)
            set("service_code", Utils.getServiceCode(doc))
            append("ci_t", ciC)
            append("mode", "W")
            append("movieIdx", "")
            append("series_title", "[]")
            append("series_data", "")
            append("headTail", "\"\"")
            append("block_key", blockKey2)
            append("memo", content)
            append("code", "")
            append("bgm", "0")
            fileNos.take(MAX_FILE_COUNT).forEachIndexed { i, fileNo ->
                append("file_write[$i][file_no]", fileNo)
            }
        }
        cs.frozen = true
        val res = try {
            client.submitForm("https://gall.dcinside.com/board/forms/article_submit", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, writeUrl)
                    xMLHttpRequest()
                }
            }
        } finally {
            cs.frozen = false
        }
        val body = res.bodyAsText()
        try {
            val s = body.split("||")
            val success = s[0].toBooleanStrict()
            if (!success) return Result(false, failCause = s[1])
            return Result(true, articleId = s[1].toInt())
        } catch (e: Exception) {
            throw InvalidResponseException("게시글 작성 응답", res.status, body, e)
        }
    }

    data class Result(
        val success: Boolean,
        val articleId: Int = 0,
        val failCause: String? = null
    )

    override fun close() {
        client.close()
    }

    override fun toString() = "ArticleWrite(gall=$gall, session=$session)"
}