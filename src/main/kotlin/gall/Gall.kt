package com.github.onlaait.dcapi.gall

import com.github.onlaait.dcapi.gall.GallType.*
import com.github.onlaait.dcapi.util.Utils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup

data class Gall(
    val id: String,
    val type: GallType
) {

    fun url(): String =
        when (type) {
            MAIN, MINOR -> "https://gall.dcinside.com/$id"
            MINI -> "https://gall.dcinside.com/mini/$id"
            PERSON -> "https://gall.dcinside.com/person/$id"
        }

    private fun urlBase(mode: String): String =
        when (type) {
            MAIN -> "https://gall.dcinside.com/board/$mode/?id=$id"
            MINOR -> "https://gall.dcinside.com/mgallery/board/$mode/?id=$id"
            MINI -> "https://gall.dcinside.com/mini/board/$mode/?id=$id"
            PERSON -> "https://gall.dcinside.com/person/board/$mode/?id=$id"
        }

    fun listUrl(): String = urlBase("lists")

    fun articleUrl(articleId: Int): String = "${urlBase("view")}&no=$articleId"

    fun commentUrl(articleId: Int): String = "${urlBase("view")}&no=$articleId&t=cv&page=1"

    fun writeUrl(): String = urlBase("write")


    fun listMobileUrl(): String = when (type) {
        MAIN, MINOR -> "https://m.dcinside.com/board/$id"
        MINI -> "https://m.dcinside.com/mini/$id"
        PERSON -> "https://m.dcinside.com/person/$id"
    }

    fun articleMobileUrl(articleId: Int): String = "${listMobileUrl()}/$articleId"

    fun commentMobileUrl(articleId: Int): String = "${articleMobileUrl(articleId)}#comment_box"

    fun writeMobileUrl(): String = when (type) {
        MAIN, MINOR -> "https://m.dcinside.com/write/$id"
        MINI -> "https://m.dcinside.com/writemini/$id"
        PERSON -> "https://m.dcinside.com/writepr/$id"
    }


    private var _nick: String? = null
    var nick: String
        get() {
            if (_nick == null) discoverGallNick()
            return _nick!!
        }
        internal set(value) {
            _nick = value
        }

    private fun discoverGallNick() {
        val body = runBlocking {
            Utils.client().use { client ->
                val res = client.get(writeUrl())
                res.bodyAsText()
            }
        }
        nick = Jsoup.parse(body).getElementById("gall_nick_name")?.`val`() ?: ""
    }


    override fun toString(): String = "Gall($id.${type.symbol})"
}
