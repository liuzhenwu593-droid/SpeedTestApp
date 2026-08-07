package com.shadiao.nb.data

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val updateLog: String = "",
    val forceUpdate: Boolean = false
)