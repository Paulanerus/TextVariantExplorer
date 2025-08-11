package dev.paulee.ui

object App {
    const val NAME = "TextVariant Explorer"

    const val APP_DIR = ".textexplorer"

    private val VERSION: String? by lazy { System.getProperty("app.version") }

    private val apiVersion: String? by lazy { System.getProperty("api.version") }

    private val coreVersion: String? by lazy { System.getProperty("core.version") }

    private val uiVersion: String? by lazy { System.getProperty("ui.version") }

    val VERSION_STRING = "v$VERSION (API - $apiVersion, Core - $coreVersion, UI - $uiVersion)"
}