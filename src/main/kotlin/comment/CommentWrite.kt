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
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging

class CommentWrite(val gall: Gall, val articleId: Int, val comment: Comment, val session: Session, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun write(): Result {
        logger.debug { "$this.write()" }
        return runBlocking { write(null, null) }
    }

    fun reply(commentId: Int, mentionCommentId: Int? = null): Result {
        logger.debug { "$this.reply(commentId=$commentId, mentionCommentId=$mentionCommentId)" }
        return runBlocking { write(commentId, mentionCommentId) }
    }

    private suspend fun write(replyCommentId: Int?, mentionCommentId: Int?): Result {
        val articleUrl = gall.commentUrl(articleId)

        val (status, body) = Utils.client(maxTries) {
            if (session is LoginSession) cookiesStorage(session.cookies) else cookiesStorage()
        }.use { client ->
            val form = run {
                val (res, body, doc) = client.readArticle(articleUrl)
                    ?: return Result(false, failCause = FailCause.ARTICLE_DELETED)
                Utils.consumeDoc(gall, doc)
                val docBody = doc.body()

                val anonymous = session is AnonymousSession
                val useGallNick = anonymous && session.name == null
                try {
                    parameters {
                        append("id", gall.id)
                        append("no", articleId)
                        when (comment) {
                            is TextComment -> {
                                if (replyCommentId == null) {
                                    append("reply_no", "undefined")
                                } else {
                                    append("c_no", replyCommentId)
                                    append("reply_no", mentionCommentId ?: replyCommentId)
                                }
                                append("name", if (useGallNick) gall.nick else (session as? AnonymousSession)?.name)
                                append("password", (session as? AnonymousSession)?.password)
                                append("memo", comment.content)
                                append("cur_t", docBody.getElementById("cur_t")!!.`val`())
                                append("check_6", docBody.getElementById("check_6")!!.`val`())
                                append("check_7", docBody.getElementById("check_7")!!.`val`())
                                append("check_8", docBody.getElementById("check_8")!!.`val`())
                                append("check_9", docBody.getElementById("check_9")!!.`val`())
                                append("check_10", docBody.getElementById("check_10")!!.`val`())
                                append("recommend", docBody.getElementById("recommend")!!.`val`())
                                append("c_r_k_x_z", docBody.getElementById("c_r_k_x_z")!!.`val`())
                                append("t_vch2", "")
                                append("t_vch2_chk", "")
                                append("c_gall_id", gall.id)
                                append("c_gall_no", articleId)
                                append("service_code", Utils.getServiceCode(doc))
                                append("g-recaptcha-response", "")
                                append("_GALLTYPE_", docBody.getElementById("_GALLTYPE_")!!.`val`())
                                append("headTail", "\"\"")
                            }

                            is DcconComment -> {
                                append("package_idx", buildString {
                                    append(comment.first.packageId)
                                    if (comment.second != null) {
                                        append(',')
                                        append(comment.second.packageId)
                                    }
                                })
                                append("detail_idx", buildString {
                                    append(comment.first.detailId)
                                    if (comment.second != null) {
                                        append(',')
                                        append(comment.second.detailId)
                                    }
                                })
                                append("double_con_chk", if (comment.second == null) "" else "1")
                                if (replyCommentId != null) {
                                    append("c_no", replyCommentId)
                                    append("reply_no", mentionCommentId ?: replyCommentId)
                                }
                                append("name", if (useGallNick) gall.nick else (session as? AnonymousSession)?.name)
                                append("password", (session as? AnonymousSession)?.password)
                                append("input_type", "comment")
                                append("t_vch2", "")
                                append("t_vch2_chk", "")
                                append("c_gall_id", gall.id)
                                append("c_gall_no", articleId)
                                append("g-recaptcha-response", "")
                                append("check_6", docBody.getElementById("check_6")!!.`val`())
                                append("check_7", docBody.getElementById("check_7")!!.`val`())
                                append("check_8", docBody.getElementById("check_8")!!.`val`())
                                append("_GALLTYPE_", docBody.getElementById("_GALLTYPE_")!!.`val`())
                            }
                        }
                        if (anonymous) {
                            append("gall_nick_name", gall.nick)
                            append("use_gall_nick", if (useGallNick) "Y" else "N")
                        }
                    }
                } catch (e: Exception) {
                    throw InvalidResponseException("게시글 읽기 응답", res.status, body, e)
                }
            }
            val url = when (comment) {
                is TextComment -> "https://gall.dcinside.com/board/forms/comment_submit"
                is DcconComment -> "https://gall.dcinside.com/dccon/insert_icon"
            }
            Thread.sleep(1000)
            val res = client.submitForm(url, form) {
                headers {
                    set(HttpHeaders.Origin, "https://gall.dcinside.com")
                    set(HttpHeaders.Referrer, articleUrl)
                    xMLHttpRequest()
                }
            }
            val body = res.bodyAsText()
            res.status to body
        }
        
        try {
            return when (comment) {
                is TextComment -> {
                    val s = body.split("||")
                    if (s.size == 1) {
                        Result(true, commentId = if (replyCommentId == null) s[0].toInt() else null)
                    } else {
                        Result(false, failCause = s[1])
                    }
                }
                is DcconComment -> {
                    if (body == "ok") {
                        Result(true)
                    } else {
                        val s = body.split("||")
                        Result(false, failCause = s[1])
                    }
                }
            }
        } catch (e: Exception) {
            throw InvalidResponseException("댓글 작성 응답", status, body, e)
        }
    }

    override fun toString() = "CommentWrite(gall=$gall, articleId=$articleId, comment=$comment, session=$session)"

    data class Result(
        val success: Boolean,
        val commentId: Int? = null,
        val failCause: String? = null
    )
}
