package com.github.onlaait.dcapi.article

enum class SearchType(val s: String) {

    SUBJECT_CONTENT("subject_m"),
    SUBJECT("subject"),
    CONTENT("memo"),
    WRITER("name"),
    COMMENT("comment");
}