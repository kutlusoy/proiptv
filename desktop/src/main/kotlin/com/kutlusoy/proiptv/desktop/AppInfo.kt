package com.kutlusoy.proiptv.desktop

import java.util.Properties

/** Version and channel of this build, written by the Gradle build into appinfo.properties. */
object AppInfo {
    private val props = Properties().also { p ->
        AppInfo::class.java.getResourceAsStream("/appinfo.properties")?.use { p.load(it) }
    }
    val version: String = props.getProperty("version", "0.0.0")
    val channel: String = props.getProperty("channel", "dev")
    val testBuild: Int = props.getProperty("testBuild", "0").toIntOrNull() ?: 0
    val isTest: Boolean get() = channel == "test"
    val label: String get() = if (isTest && testBuild > 0) "$version (test $testBuild)" else version
}
