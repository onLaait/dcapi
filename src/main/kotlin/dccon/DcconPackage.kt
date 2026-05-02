package com.github.onlaait.dcapi.dccon

@ConsistentCopyVisibility
data class DcconPackage internal constructor(
    val packageId: Int,
    val title: String,
    val list: List<Dccon>
)
