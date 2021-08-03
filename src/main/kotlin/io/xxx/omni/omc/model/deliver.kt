package io.xxx.omni.omc.model

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.springframework.stereotype.Repository

data class Delivery(
    @TableId
    var id: Long?,
    var sid: String?,
    var sn: String?,
    var receiverPhone: String?,
    var receiverState: String?,
    var receiverCity: String?,
    var receiverDistrict: String?,
    var receiverAddress: String?,
    @TableField(exist = false)
    var item: List<DeliveryItem>?
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null, null)
}

@Mapper
@Repository
interface DeliveryMapper : BaseMapper<Delivery>

data class DeliveryItem(
    @TableId
    var id: Long?,
    var num: Int?,
    var title: String?,
    var skuId: String?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null)
}

@Mapper
@Repository
interface DeliveryItemMapper : BaseMapper<DeliveryItem>