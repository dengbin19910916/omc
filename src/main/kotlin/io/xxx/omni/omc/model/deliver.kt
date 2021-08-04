package io.xxx.omni.omc.model

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

data class Delivery(
    @TableId(type = IdType.AUTO)
    var id: Long?,
    var sid: String?,
    var sn: String?,
    var receiverName: String?,
    var receiverPhone: String?,
    var receiverState: String?,
    var receiverCity: String?,
    var receiverDistrict: String?,
    var receiverAddress: String?,
    var created: LocalDateTime?,
    var modified: LocalDateTime?,
    @TableField(exist = false)
    var item: List<DeliveryItem>?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null)

    constructor(
        sid: String,
        sn: String,
        receiverName: String,
        receiverPhone: String,
        receiverState: String? = null,
        receiverCity: String? = null,
        receiverDistrict: String? = null,
        receiverAddress: String? = null,
    ) : this(
        null,
        sid,
        sn,
        receiverName,
        receiverPhone,
        receiverState,
        receiverCity,
        receiverDistrict,
        receiverAddress,
        null,
        null,
        null
    ) {
        val now = LocalDateTime.now()
        this.created = now
        this.modified = now
    }
}

@Mapper
@Repository
interface DeliveryMapper : BaseMapper<Delivery>

data class DeliveryItem(
    @TableId(type = IdType.AUTO)
    var id: Long?,
    var did: Long?,
    var skuId: String?,
    var num: Int?,
    var title: String?
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null)

    constructor(did: Long, skuId: String, num: Int, title: String? = null) : this(null, did, skuId, num, title)
}

@Mapper
@Repository
interface DeliveryItemMapper : BaseMapper<DeliveryItem>