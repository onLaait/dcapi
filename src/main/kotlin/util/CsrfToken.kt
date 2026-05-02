package com.github.onlaait.dcapi.util

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging
import org.jsoup.Jsoup
import java.time.LocalTime
import java.time.ZonedDateTime

object CsrfToken : Logging {

    private val EXPIRE_TIME = LocalTime.MIDNIGHT
    private const val ENOUGH_SECS: Long = 15

    private var nextTime = 0L
    private lateinit var csrfToken: String

    @Synchronized
    fun get(): String {
        if (System.currentTimeMillis() >= nextTime) runBlocking { renew() }
        return csrfToken
    }

    private suspend fun renew() {
        logger.debug { "CsrfToken.renew()" }
        val body = Utils.mobileClient().use { client ->
            val res = client.get("https://m.dcinside.com/")
            res.bodyAsText()
        }

        nextTime = run {
            val now = ZonedDateTime.now(Utils.KR_ZONE)
            val dt0 = now.with(EXPIRE_TIME)
            val dt1 = dt0.minusSeconds(ENOUGH_SECS)
            if (now < dt1) {
                dt1.toInstant().toEpochMilli()
            } else if (now > dt0.plusSeconds(ENOUGH_SECS)) {
                dt1.plusDays(1).toInstant().toEpochMilli()
            } else {
                0L
            }
        }

        val doc = Jsoup.parse(body)
        csrfToken = doc.selectFirst("meta[name=\"csrf-token\"]")!!.attr("content")
    }
}