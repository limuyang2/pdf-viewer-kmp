package io.github.limuyang2.pdfdemo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform