package io.xxx.omni.omc.trans

import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 将订单信息转换为发货信息，
 * 将退单信息转换为退货信息。
 */
@Component
@KafkaListener(topics = ["TAOBAO-TRADE"])
class TbTranslator {

    @KafkaHandler
    fun transform(value: String) {

    }
}