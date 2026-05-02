package com.github.onlaait.dcapi.article

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.session.LoginSession
import com.github.onlaait.dcapi.util.FailCause
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.readArticle
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.addCookie
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import com.github.onlaait.httputil.HttpUtils.cookiesStorageOf
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ArticleVote(val gall: Gall, val articleId: Int, val session: LoginSession? = null, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun upvote(): Result {
        logger.debug { "$this.upvote()" }
        return runBlocking { vote("U") }
    }

    fun downvote(): Result {
        logger.debug { "$this.downvote()" }
        return runBlocking { vote("D") }
    }

    private suspend fun vote(voteMode: String): Result {
        val articleUrl = gall.commentUrl(articleId)

        val cs = if (session == null) cookiesStorageOf() else cookiesStorageOf(session.cookies)
        Utils.client(maxTries) {
            cookiesStorage(cs)
        }.use { client ->
            val doc: Document
            val ciC: String
            run {
                val (res, body) = client.readArticle(articleUrl)
                    ?: return Result(false, votes = 0, failCause = FailCause.ARTICLE_DELETED)
                try {
                    doc = Jsoup.parse(body)
                    ciC = res.setCookie().first { it.name == "ci_c" }.value
                } catch (e: Exception) {
                    throw InvalidResponseException("게시글 읽기 응답", res.status, body, e)
                }
            }
            Utils.consumeDoc(gall, doc)

            var rcmdCookie = "${gall.id}${articleId}_Firstcheck"
            if (voteMode == "D") rcmdCookie += "_down"
            cs.addCookie(Cookie(rcmdCookie, "Y", domain = "dcinside.com", path = "/"))
            val form = Utils.serialize(doc.body().getElementById("_view_form_")!!) + parameters {
                append("ci_t", ciC)
                append("id", gall.id)
                append("no", articleId)
                append("mode", voteMode)
                append("code_recommend", "")
                append("_GALLTYPE_", doc.getElementById("_GALLTYPE_")!!.`val`())
                append("link_id", gall.id)
            }
            val res = client.submitForm("https://gall.dcinside.com/board/recommend/vote", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, articleUrl)
                    xMLHttpRequest()
                }
            }
            val body = res.bodyAsText()
            try {
                val s = body.split("||")
                if (s[0] == "true") return Result(true, votes = s[1].toInt())
                require(s[0] == "false" && s.size == 2)
                return Result(false, votes = doc.body().selectFirst(".up_num")!!.text().toInt(), failCause = s[1])
            } catch (e: Exception) {
                throw InvalidResponseException("추천 응답", res.status, body, e)
            }
        }
    }

    override fun toString() = "ArticleVote(gall=$gall, articleId=$articleId, session=$session)"

    data class Result(
        val success: Boolean,
        val votes: Int,
        val failCause: String? = null
    )
}