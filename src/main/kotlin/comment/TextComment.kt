package com.github.onlaait.dcapi.comment

data class TextComment(
    val content: String
) : Comment, WrittenComment
