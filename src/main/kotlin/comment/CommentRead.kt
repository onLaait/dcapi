package com.github.onlaait.dcapi.comment

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.user.AnonymousUser
import com.github.onlaait.dcapi.user.LoginUser
import com.github.onlaait.dcapi.user.User
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.readArticle
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class CommentRead(val gall: Gall, val articleId: Int, val maxTries: Int = Dcapi.maxTries) : Logging {

    private companion object {
        val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
        val DCCON_RGX = Regex("^<(img|video) class")
        val SOURCE_RGX = Regex("<source [^>]+>")
        val SRC_RGX = Regex(" (data-)?src=\"(?<src>[^\" ]+)\"")
        val FOCUS_COMMENT_RGX = Regex("<a +href=\"javascript:\\w+\\((?<id>\\d+)\\).*;\" +class=\".*mention.*\" *> *@.+ *</a>")
    }

    private var ready = false
    private lateinit var e_s_n_o: String
    private lateinit var _GALLTYPE_: String
    private lateinit var secret_article_key: String

    val articleUrl = gall.commentUrl(articleId)

    fun get(page: Int = 1): Result? {
        logger.debug { "$this.get(page=$page)" }
        val form = {
            parameters {
                append("id", gall.id)
                append("no", articleId)
                append("cmt_id", gall.id)
                append("cmt_no", articleId)
                append("focus_cno", "")
                append("focus_pno", "")
                append("e_s_n_o", e_s_n_o)
                append("comment_page", page)
                append("sort", "D")
                append("prevCnt", "0")
                append("board_type", "")
                append("_GALLTYPE_", _GALLTYPE_)
                append("secret_article_key", secret_article_key)
            }
        }
        return runBlocking { _get(form) }
    }

    fun getFocused(commentId: Int): Result? {
        logger.debug { "$this.getFocused(commentId=$commentId)" }
        val form = {
            parameters {
                append("id", gall.id)
                append("no", articleId)
                append("cmt_id", gall.id)
                append("cmt_no", articleId)
                append("focus_cno", commentId)
                append("focus_pno", "")
                append("e_s_n_o", e_s_n_o)
                append("comment_page", "1")
                append("sort", "D")
                append("prevCnt", "")
                append("board_type", "")
                append("_GALLTYPE_", _GALLTYPE_)
                append("secret_article_key", "")
            }
        }
        return runBlocking { _get(form) }
    }

    private suspend fun _get(form: () -> Parameters): Result? {
        if (!ready()) return null

        val form = form()
        val (status, body) = Utils.client(maxTries).use { client ->
            val res = client.submitForm("https://gall.dcinside.com/board/comment/", form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, articleUrl)
                    xMLHttpRequest()
                }
            }
            res.status to res.bodyAsText()
        }
        val now = LocalDateTime.now()

        val comments = mutableListOf<CommentData>()
        val totalCount: Int
        try {
            val o = Json.parseToJsonElement(body).jsonObject
            for (e in o["comments"]!!.jsonArray) {
                val o = e.jsonObject
                if (o["parent"]?.jsonPrimitive?.intOrNull != articleId) continue
                if (o["is_delete"]!!.jsonPrimitive.int != 0) continue
                val replyId = o["c_no"]!!.jsonPrimitive.int.takeIf { it > 0 }
                var memo = o["memo"]!!.jsonPrimitive.contentOrNull!!
                comments += CommentData(
                    id = o["no"]!!.jsonPrimitive.int,
                    replyId = replyId,
                    mentionId = run {
                        if (replyId != null) {
                            val mat = FOCUS_COMMENT_RGX.find(memo)
                            if (mat != null) {
                                memo = memo.removeRange(mat.range).trim()
                                return@run mat.groups["id"]!!.value.toInt()
                            }
                        }
                        null
                    },
                    writer = run {
                        val name = o["name"]!!.jsonPrimitive.contentOrNull!!
                        val id = o["user_id"]?.jsonPrimitive?.contentOrNull
                        if (id?.isNotBlank() == true) {
                            LoginUser(name, id)
                        } else {
                            val nickType = o["nicktype"]!!.jsonPrimitive.int
                            val ip = o["ip"]?.run { jsonPrimitive.contentOrNull!! } ?: ""
                            AnonymousUser(name, nickType == 1 || ip.isBlank(), ip)
                        }
                    },
                    time = run {
                        val date = o["reg_date"]!!.jsonPrimitive.contentOrNull!!
                        val s = date.split('.', limit = 2)[0]
                        if (s.length == 2) {
                            val dt = LocalDateTime.parse("${now.year}.$date", DATE_TIME_FORMATTER)
                            return@run if (ChronoUnit.DAYS.between(now, dt) >= 1) {
                                LocalDateTime.parse("${now.year - 1}.$date", DATE_TIME_FORMATTER)
                            } else {
                                dt
                            }
                        }
                        LocalDateTime.parse(date, DATE_TIME_FORMATTER)
                    },
                    comment = run {
                        if (memo.contains(DCCON_RGX) && memo.contains("written_dccon")) {
                            val memo = memo.replace(SOURCE_RGX, "")
                            val mats = SRC_RGX.findAll(memo).iterator()
                            val con1 = Url(mats.next().groups["src"]!!.value).parameters["no"]!!
                            val con2 =
                                if (mats.hasNext()) {
                                    Url(mats.next().groups["src"]!!.value).parameters["no"]!!
                                } else {
                                    null
                                }
                            return@run WrittenDcconComment(con1, con2)
                        }
                        TextComment(Jsoup.parseBodyFragment(memo).body().wholeText())
                    }
                )
            }
            totalCount = o["total_cnt"]!!.jsonPrimitive.int
        } catch (e: Exception) {
            throw InvalidResponseException("댓글 읽기 응답", status, body, e)
        }
        return Result(comments, totalCount)
    }

    internal suspend fun ready(): Boolean {
        if (ready) return true
        val doc = run {
            val body = Utils.client(maxTries).use { client ->
                val (_, body) = client.readArticle(articleUrl) ?: return false
                body
            }
            Jsoup.parse(body)
        }
        Utils.consumeDoc(gall, doc)
        e_s_n_o = doc.getElementById("e_s_n_o")!!.`val`()
        _GALLTYPE_ = doc.getElementById("_GALLTYPE_")!!.`val`()
        secret_article_key = doc.getElementById("secret_article_key")!!.`val`()
        ready = true
        return true
    }

    override fun toString() = "CommentRead(gall=$gall, articleId=$articleId)"

    data class Result(
        val comments: List<CommentData> = emptyList(),
        val totalCount: Int = 0
    )

    data class CommentData(
        val id: Int,
        val replyId: Int?,
        val mentionId: Int?,
        val writer: User,
        val time: LocalDateTime,
        val comment: WrittenComment
    )
}