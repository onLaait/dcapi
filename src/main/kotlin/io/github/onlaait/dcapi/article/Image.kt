package io.github.onlaait.dcapi.article

import io.github.onlaait.dcapi.Dcapi
import io.github.onlaait.dcapi.util.Utils
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.Logging

data class Image(
    val url: String,
    val name: String
) : Logging {

    fun download(maxTries: Int = Dcapi.maxTries): ByteArray {
        logger.debug { "$this.download(maxTries=$maxTries)" }
        return runBlocking { _download(maxTries) }
    }

    private suspend fun _download(maxTries: Int): ByteArray =
        Utils.client(maxTries).use { client ->
            val res = client.get(url) {
                headers {
                    set(HttpHeaders.Referrer, "https://gall.dcinside.com/")
                }
            }
            res.bodyAsBytes()
        }
}