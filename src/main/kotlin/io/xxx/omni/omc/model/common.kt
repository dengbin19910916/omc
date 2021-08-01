package io.xxx.omni.omc.model

import java.time.LocalDateTime

/**
 * 平台（外部）
 */
data class Platform(
    var id: String,
    var name: String,
    var enabled: Boolean,
)

/**
 * 店铺（外部）
 */
data class Store(
    var id: String,
    var pid: String,
    /**
     * 平台商家id
     */
    var oid: String?,
    var name: String,
    var enabled: Boolean,
    var appKey: String?,
    var appSecret: String?,
    var accessToken: String?,
    var created: LocalDateTime,

    var platform: Platform?,
)