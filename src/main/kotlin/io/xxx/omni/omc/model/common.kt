package io.xxx.omni.omc.model

import java.time.LocalDateTime

/**
 * 平台
 */
data class Platform(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

/**
 * 店铺
 */
data class Store(
    val id: String,
    val pid: String,
    /**
     * 平台商家id
     */
    val oid: String?,
    val name: String,
    val enabled: Boolean,
    val appKey: String?,
    val appSecret: String?,
    val accessToken: String?,
    val created: LocalDateTime,

    var platform: Platform?,
)