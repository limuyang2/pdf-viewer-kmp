package io.github.limuyang2.pdfdemo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Pdfdemo",
    ) {
        App()
    }
}