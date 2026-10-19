package com.tarang.launcher.data

import com.tarang.launcher.R

/** Local tile appearance. Package names and launch targets stay unchanged. */
object AppPresentation {
    private fun isSmartTube(packageName: String): Boolean =
        packageName == "org.smarttube.stable" || packageName == "org.smarttube.beta"

    fun label(packageName: String, original: String): String =
        if (isSmartTube(packageName)) "YouTube" else original

    fun banner(packageName: String): Int? =
        if (isSmartTube(packageName)) R.drawable.youtube_banner else null

    fun accent(packageName: String): Int? =
        if (isSmartTube(packageName)) 0xFFFF0000.toInt() else null
}
