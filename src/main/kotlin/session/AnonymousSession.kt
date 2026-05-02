package com.github.onlaait.dcapi.session

data class AnonymousSession(
    val name: String? = null,
    override val password: String
) : Session {

    override fun toString() = "AnonymousSession($name)"
}
