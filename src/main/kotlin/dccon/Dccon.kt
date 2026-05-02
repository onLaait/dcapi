package com.github.onlaait.dcapi.dccon

data class Dccon(
    val packageId: Int,
    val detailId: Int,
    val code: String,
    val title: String
) {
    val src: String = "https://dcimg5.dcinside.com/dccon.php?no=$code"
}
