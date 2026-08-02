package io.github.onlaait.dcapi.comment

import io.github.onlaait.dcapi.dccon.Dccon

data class DcconComment(
    val first: Dccon,
    val second: Dccon? = null
): Comment
