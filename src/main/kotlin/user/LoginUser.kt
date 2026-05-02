package com.github.onlaait.dcapi.user

data class LoginUser(
    override val name: String,
    val id: String
) : User
