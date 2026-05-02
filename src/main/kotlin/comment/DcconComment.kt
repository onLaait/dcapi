package com.github.onlaait.dcapi.comment

import com.github.onlaait.dcapi.dccon.Dccon

data class DcconComment(
    val first: Dccon,
    val second: Dccon? = null
): Comment
