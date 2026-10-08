package io.github.onlaait.dcapi.comment

data class TextconComment(
    val text: String,
    val backgroundColor: String,
    val textColor: String
) : Comment, WrittenComment
