package com.github.onlaait.dcapi.article

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.user.AnonymousUser
import com.github.onlaait.dcapi.user.LoginUser
import com.github.onlaait.dcapi.user.User
import com.github.onlaait.dcapi.util.CsrfToken
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.absoulteId
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.defaultRetryConfig
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities

class ArticleList(val gall: Gall, val listCount: Int = 50, val searchKeyword: String? = null, val searchType: SearchType? = null, val maxTries: Int = Dcapi.maxTries) : Logging {

    fun get(page: Int = 1, from: Int? = null): List<Item> {
        logger.debug { "$this.get(page=$page)" }
        val form = parameters {
            if (searchKeyword == null && searchType == null) {
                require(from == null)
                append("id", gall.absoulteId())
                append("page", page)
            } else {
                require(searchKeyword != null && searchType != null)
                append("id", gall.absoulteId())
                append("page", page)
                append("s_type", searchType.s)
                append("serval", searchKeyword)
                if (from != null) append("s_pos", -from)
            }
        }
        return runBlocking { _get(form) }
    }

    private suspend fun _get(form: Parameters): List<Item> {
        val (status, body) = Utils.mobileClient(maxTries).use { client ->
            val res = client.submitForm("https://m.dcinside.com/ajax/response-list", form) {
                retry {
                    defaultRetryConfig(maxTries.let { if (it >= 1) it - 1 else Int.MAX_VALUE })
                    retryIf { _, res ->
                        res.status.value == 429 ||
                            run {
                                val body = runBlocking { res.bodyAsText() }
                                val o = Json.parseToJsonElement(body).jsonObject
                                o["result"]?.jsonPrimitive?.boolean == false
                            }
                    }
                    modifyRequest {
                        it.headers {
                            set("x-csrf-token", CsrfToken.get())
                        }
                    }
                }
                headers {
                    set(HttpHeaders.Origin, "https://m.dcinside.com")
                    set(HttpHeaders.Referrer, gall.listMobileUrl())
                    set("x-csrf-token", CsrfToken.get())
                    xMLHttpRequest()
                }
                cookie("list_count", listCount.toString())
            }
            res.status to res.bodyAsText()
        }
        val list = mutableListOf<Item>()
        val searchComment = searchType == SearchType.COMMENT
        try {
            val o = Json.parseToJsonElement(body).jsonObject
            val datas = o["gall_list"]!!
                .jsonObject["data"]!!
                .jsonArray
            for (e in datas) {
                val o = e.jsonObject
                if (o["headnum"]!!.jsonPrimitive.int <= -2000000000) continue
                val icon = o["title_icon"]!!.jsonPrimitive.contentOrNull!!
                list += Item(
                    id = o["no"]!!.jsonPrimitive.int,
                    head = o["headtext"]!!.jsonPrimitive.contentOrNull?.takeIf { it.isNotEmpty() },
                    icon = when (icon) {
                        "sp-lst-txt" -> Icon.TEXT
                        "sp-lst-img" -> Icon.IMAGE
                        "sp-lst-play" -> Icon.VIDEO
                        "sp-lst-recotxt" -> Icon.RECOMMEND_TEXT
                        "sp-lst-recoimg" -> Icon.RECOMMEND_IMAGE
                        "sp-lst-recoplay" -> Icon.RECOMMEND_VIDEO
                        else -> Icon.UNKNOWN
                    },
                    subject = Jsoup.parseBodyFragment(o["subject"]!!.jsonPrimitive.contentOrNull!!).body().wholeText(),
                    hasVote = o["tail_icon"]!!.jsonPrimitive.content.contains("votelst"),
                    viewCount = o["hit"]!!.jsonPrimitive.int,
                    writer = run {
                        val name = Entities.unescape(o["name_ori"]!!.jsonPrimitive.contentOrNull!!)
                        val id = o["user_id"]!!.jsonPrimitive.contentOrNull
                        if (!id.isNullOrEmpty()) {
                            LoginUser(name, id)
                        } else {
                            AnonymousUser(name, name == gall.nick, o["ip"]!!.jsonPrimitive.contentOrNull!!)
                        }
                    },
                    upvoteCount = o["recommend"]!!.jsonPrimitive.int,
                    commentCount = o["total_comment"]!!.jsonPrimitive.int,
                    commentId = if (searchComment) o["comment_no"]!!.jsonPrimitive.int else 0,
                    commentContent = if (searchComment) Entities.unescape(o["comment_memo"]!!.jsonPrimitive.contentOrNull!!) else ""
                )
            }
            return list
        } catch (e: Exception) {
            throw InvalidResponseException("글 목록 응답", status, body, e)
        }
    }

    override fun toString(): String = "ArticleList(gall=$gall, listCount=$listCount, searchKeyword=$searchKeyword, searchType=$searchType)"

    @ConsistentCopyVisibility
    data class Item internal constructor(
        val id: Int,
        val head: String?,
        val icon: Icon,
        val subject: String,
        val hasVote: Boolean,
        val writer: User,
        val viewCount: Int,
        val upvoteCount: Int,
        val commentCount: Int,
        val commentId: Int,
        val commentContent: String
    )

    enum class Icon(val mightHaveImage: Boolean, val hasVideo: Boolean, val isRecommend: Boolean) {
        TEXT(false, false, false),
        IMAGE(true, false, false),
        VIDEO(true, true, false),
        RECOMMEND_TEXT(false, false, true),
        RECOMMEND_IMAGE(true, false, true),
        RECOMMEND_VIDEO(true, true, true),
        UNKNOWN(false, false, false);
    }
}