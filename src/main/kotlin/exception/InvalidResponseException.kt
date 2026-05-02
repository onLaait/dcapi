package com.github.onlaait.dcapi.exception

import io.ktor.http.*

class InvalidResponseException(message: String, val status: HttpStatusCode, val body: String, cause: Throwable? = null) : RuntimeException("$message: status=$status, body=$body", cause)