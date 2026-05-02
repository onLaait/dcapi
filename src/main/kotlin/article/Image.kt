package com.github.onlaait.dcapi.article

import com.github.onlaait.dcapi.util.Utils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging

@ConsistentCopyVisibility
data class Image internal constructor(
    val url: String,
    val name: String
) : Logging {

    fun download(): ByteReadChannel {
        logger.debug { "$this.download()" }
        return runBlocking { _download() }
    }

    private suspend fun _download(): ByteReadChannel =
        Utils.client().use { client ->
            val res = client.get(url) {
                headers {
                    set(HttpHeaders.Referrer, "https://gall.dcinside.com/")
                }
            }
            res.bodyAsChannel()
        }
}