package com.shadiao.nb.data

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val downloadUrl: String = "",
    val updateMessage: String = "",
    val forceUpdate: Boolean = false
)
