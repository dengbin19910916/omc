package io.xxx.omni.omc.trans

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import io.xxx.omni.omc.model.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.AcknowledgingMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

abstract class Translator : AcknowledgingMessageListener<String, String> {

    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    protected lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    protected lateinit var documentMapper: DocumentMapper

    @Autowired
    protected lateinit var deliveryMapper: DeliveryMapper

    @Autowired
    protected lateinit var deliveryItemMapper: DeliveryItemMapper

    @Autowired
    protected lateinit var committedOffsetMapper: CommittedOffsetMapper

    protected fun getDocument(sid: String, sn: String): Document? {
        val qw = KtQueryWrapper(Document::class.java)
            .eq(Document::sid, sid)
            .eq(Document::sn, sn)
        return documentMapper.selectOne(qw)
    }
}

/**
 * 将订单信息转换为发货信息，
 * 将退单信息转换为退货信息。
 */
@Component
class TbTranslator : Translator() {

    @KafkaListener(topics = ["TAOBAO_TRADE"])
    override fun onMessage(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val json = JSON.parseObject(record.value())
        val sid = json.getString("sid")
        val sn = json.getString("sn")
        val document = getDocument(sid, sn)
        if (document == null) {
            log.error("报文[${record.value()}]不存在")
            return
        }

        val data = JSON.parseObject(document.data!!)

        val delivery = Delivery(
            sid,
            sn,
            data.getString("receiver_name"),
            data.getString("receiver_phone"),
            data.getString("receiver_state"),
            data.getString("receiver_city"),
            data.getString("receiver_district"),
            data.getString("receiver_address"),
        )
        transactionTemplate.executeWithoutResult {
            deliveryMapper.insert(delivery)
            val deliveryItems = data
                .getJSONObject("orders")
                .getJSONArray("order").map {
                    @Suppress("unchecked_cast")
                    val order = JSONObject(it as Map<String, Any>)
                    DeliveryItem(
                        delivery.id!!,
                        order.getString("sku_id"),
                        order.getIntValue("num"),
                        order.getString("title"),
                    )
                }
            for (deliveryItem in deliveryItems) {
                deliveryItemMapper.insert(deliveryItem)
            }
        }

        val committedOffset = CommittedOffset(record.topic(), record.partition(), record.offset())
        committedOffsetMapper.insert(committedOffset)
        acknowledgment.acknowledge()
    }
}