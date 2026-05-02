package com.github.onlaait.dcapi.comment

data class WrittenDcconComment(
    val firstCode: String,
    val secondCode: String? = null
) : WrittenComment
