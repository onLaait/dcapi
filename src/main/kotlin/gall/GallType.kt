package com.github.onlaait.dcapi.gall

enum class GallType {

    MAIN {
        override val symbol = "G"
    },

    MINOR {
        override val symbol = "M"
    },

    MINI {
        override val symbol = "MI"
    },

    PERSON {
        override val symbol = "PR"
    };

    abstract val symbol: String
}
