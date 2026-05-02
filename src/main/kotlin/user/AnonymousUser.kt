package com.github.onlaait.dcapi.user

data class AnonymousUser(
    override val name: String,
    val isGallNick: Boolean,
    val ip: String
) : User
