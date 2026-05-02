package com.github.onlaait.dcapi.session

import com.github.onlaait.dcapi.Dcapi
import com.github.onlaait.dcapi.exception.InvalidResponseException
import com.github.onlaait.dcapi.util.Utils
import com.github.onlaait.dcapi.util.Utils.xMLHttpRequest
import com.github.onlaait.httputil.HttpUtils.append
import com.github.onlaait.httputil.HttpUtils.cookiesStorage
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.Logging

class ChangeNick(val session: LoginSession, val maxTries: Int = Dcapi.maxTries) : Logging {

    private var ready = false
    private lateinit var token: String

    fun change(nick: String, nickType: NickType): Result = runBlocking { _change(nick, nickType) }

    private suspend fun _change(nick: String, nickType: NickType): Result {
        ready()
        Utils.client {
            cookiesStorage(session.cookies)
        }.use { client ->
            val form = parameters {
                append("user_nick", nick)
                append("nick_type", nickType.code)
                append("token", token)
                append("auth_type", "")
                append("auth_sign", "")
                append("auth_code", "")
            }
            val res = client.submitForm("https://sign.dcinside.com/myinfo/modifyAjax?action=updateNick", form) {
                headers {
                    set(HttpHeaders.Origin, "https://sign.dcinside.com")
                    set(HttpHeaders.Referrer, "https://sign.dcinside.com/myinfo/modify?action=basic&token=$token")
                    xMLHttpRequest()
                }
            }
            val body = res.bodyAsText()
            try {
                val o = Json.parseToJsonElement(body).jsonObject
                val resultCode = o["resultCode"]!!.jsonPrimitive.int
                if (resultCode != 1) return Result(false, o["resultMsg"]?.jsonPrimitive?.contentOrNull)
                require(resultCode == 1)
                session.name = nick
                return Result(true)
            } catch (e: Exception) {
                throw InvalidResponseException("닉네임 변경 응답", res.status, body, e)
            }
        }
    }

    private suspend fun ready() {
        if (ready) return
        Utils.client {
            cookiesStorage(session.cookies)
        }.use { client ->
            val res = client.post("https://sign.dcinside.com/myinfo/modifyAjax?action=checkPW") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("ci_t", session.cookies.first { it.name == "ci_c" }.value)
                        append("token", "")
                        append("otp_use_ref", "")
                        append("ori_action", "basic")
                        append("password_chk", session.password)
                    },
                    Utils.generateBoundary()
                ))
                headers {
                    set(HttpHeaders.Origin, "https://sign.dcinside.com")
                    set(HttpHeaders.Referrer, "https://sign.dcinside.com/myinfo/pw_check?action=basic")
                    xMLHttpRequest()
                }
            }
            val body = res.bodyAsText()
            token = try {
                require(res.status.value == 200)
                val o = Json.parseToJsonElement(body).jsonObject
                require(o["resultCode"]!!.jsonPrimitive.int == 1)
                o["token"]!!.jsonPrimitive.contentOrNull!!
            } catch (e: Exception) {
                throw InvalidResponseException("기본 정보 변경 비밀번호 확인 응답", res.status, body, e)
            }
            ready = true
        }
    }

    enum class NickType(val code: Int) {
        NON_FIXED(1),
        FIXED(2);
    }

    data class Result(
        val success: Boolean,
        val failCause: String? = null
    )
}