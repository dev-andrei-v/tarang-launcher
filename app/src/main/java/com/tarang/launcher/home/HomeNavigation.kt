package com.tarang.launcher.home

/** A repeated Home or Apps command must reach the existing launcher screen. */
data class HomeNavigation(val sequence: Long = 0, val showAllApps: Boolean = false, val startFrame: Boolean = false)
