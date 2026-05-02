package com.github.onlaait.dcapi.comment

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.session.AnonymousSession
import com.github.onlaait.dcapi.session.LoginSession
import com.github.onlaait.dcapi.session.Session
import com.github.onlaait.dcapi.util.FailCause
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.readArticle
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class CommentDelete(val gall: Gall, val articleId: Int, val commentId: Int, val session: Session, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun delete(): Result {
        logger.debug { "$this.delete()" }
        return runBlocking { _delete() }
    }

    private suspend fun _delete(): Result {
        val articleUrl = gall.commentUrl(articleId)
        val status: HttpStatusCode
        val body: String
        Utils.client(maxTries) {
            if (session is LoginSession) cookiesStorage(session.cookies)
        }.use { client ->
            val doc: Document
            val ciC: String
             run {
                 val (res, body) = client.readArticle(articleUrl)
                     ?: return Result(false, failCause = FailCause.ARTICLE_DELETED)
                 doc = Jsoup.parse(body)
                 ciC = res.setCookie()["ci_c"]!!.value
            }
            Utils.consumeDoc(gall, doc)

            val form = parameters {
                append("no", articleId)
                append("v_cur_t", doc.selectFirst("input[name=v_cur_t]")!!.`val`())
                append("id", gall.id)
                append("re_no", commentId)
                append("mode", "del")
                append("_GALLTYPE_", doc.getElementById("_GALLTYPE_")!!.`val`())
                if (session is AnonymousSession) append("re_password", session.password)
                append("g-recaptcha-response", "")
                append("ci_t", ciC)
                append("c_k_v", "dzc")
            }
            val res = client.submitForm("https://gall.dcinside.com/board/comment/comment_delete_submit", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, articleUrl)
                    xMLHttpRequest()
                }
            }
            status = res.status
            body = res.bodyAsText()
        }
        try {
            val s = body.split("||", limit = 2)
            if (s[0] == "true") return Result(true)
            require(s[0] == "false")
            return Result(false, failCause = s[1])
        } catch (e: Exception) {
            throw InvalidResponseException("댓글 삭제 응답", status, body, e)
        }
    }

    override fun toString() = "CommentDelete(gall=$gall, articleId=$articleId, commentId=$commentId, sessionsession=$session)"

    data class Result(
        val success: Boolean,
        val failCause: String? = null
    )
}
