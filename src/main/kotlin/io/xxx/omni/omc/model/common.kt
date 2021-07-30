package io.xxx.omni.omc.model

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import java.time.LocalDateTime

/**
 * 平台（外部）
 */
data class Platform(
    @TableId
    var id: String?,
    var name: String?,
    var enabled: Boolean?,
)

/**
 * 店铺（外部）
 */
data class Store(
    @TableId(type = IdType.AUTO)
    var id: String?,
    var pid: String?,
    /**
     * 平台商家id
     */
    var oid: String?,
    var name: String?,
    var enabled: Boolean?,
    var appKey: String?,
    var appSecret: String?,
    var accessToken: String?,
    var created: LocalDateTime?,
    @TableField(exist = false)
    var platform: Platform?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null, null, null)
}