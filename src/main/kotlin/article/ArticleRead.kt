package com.github.onlaait.dcapi.article

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.gall.Gall
import com.github.onlaait.dcapi.user.AnonymousUser
import com.github.onlaait.dcapi.user.LoginUser
import com.github.onlaait.dcapi.user.User
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.readArticle
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ArticleRead(val gall: Gall, val articleId: Int, val maxTries: Int = Dcapi.maxTries) : Logging {

    private companion object {
        val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    fun get(): Result? {
        logger.debug { "$this.get()" }
        return runBlocking { _get() }
    }

    private suspend fun _get(): Result? {
        val url = gall.articleUrl(articleId)
        val (res, body) = Utils.client(maxTries).use { client ->
            client.readArticle(url) ?: return null
        }
        try {
            val doc = Jsoup.parse(body)
            Utils.consumeDoc(gall, doc)
            val article = doc.body().selectFirst("#container .view_content_wrap")!!
            val header = article.selectFirst("header")!!
            return Result(
                head = header.selectFirst(".title_headtext")?.text()?.takeIf { it.isNotEmpty() }?.removeSurrounding("[", "]"),
                subject = header.selectFirst(".title_subject")!!.text(),
                writer = run {
                    val e = header.selectFirst(".gall_writer")!!
                    val nick = e.attr("data-nick")
                    val id = e.attr("data-uid")
                    if (id.isNotBlank()) {
                        LoginUser(nick, id)
                    } else {
                        val gallNick = doc.body().selectFirst("[name=\"gall_nick_name\"], [name=\"name\"]")!!.`val`()
                        val ip = e.attr("data-ip")
                        AnonymousUser(nick, nick == gallNick, ip)
                    }
                },
                time = run {
                    val date = header.selectFirst("header .gall_date")!!.attr("title")
                    LocalDateTime.parse(date, DATE_FORMATTER)
                },
                viewCount = header.selectFirst(".gall_count")!!.text().filter { it.isDigit() }.toInt(),
                upvoteCount = article.selectFirst(".up_num")!!.text().toInt(),
                downvoteCount = article.selectFirst(".down_num")?.text()?.toInt() ?: 0,
                commentCount = header.selectFirst(".gall_comment")!!.text().filter { it.isDigit() }.toInt(),
                content = Document("").apply {
                    val div = article.selectFirst(".write_div")!!.clone()
                    appendChildren(div.children())
                },
                images = article.select(".appending_file_box li").map {
                    val a = it.selectFirst("a")!!
                    Image(url = a.attr("href"), name = a.text())
                }
            )
        } catch (e: Exception) {
            throw InvalidResponseException("게시글 읽기 응답", res.status, body, e)
        }
    }

    override fun toString() = "ArticleRead(gall=$gall, articleId=$articleId)"

    data class Result(
        val head: String?,
        val subject: String,
        val writer: User,
        val time: LocalDateTime,
        val viewCount: Int,
        val upvoteCount: Int,
        val downvoteCount: Int,
        val commentCount: Int,
        val content: Document,
        val images: List<Image>
    )
}