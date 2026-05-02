package com.github.onlaait.dcapi.session

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.util.*

object LoginSessionAsStringSerializer : KSerializer<LoginSession> {

    override val descriptor = PrimitiveSerialDescriptor("com.github.onlaait.dcapi.LoginSessionAsString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LoginSession) {
        val json = Json.encodeToString(LoginSession.generatedSerializer(), value)
        val string = Base64.getEncoder().encodeToString(json.toByteArray())
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): LoginSession {
        val string = decoder.decodeString()
        val json = String(Base64.getDecoder().decode(string))
        return Json.decodeFromString(LoginSession.generatedSerializer(), json)
    }
}