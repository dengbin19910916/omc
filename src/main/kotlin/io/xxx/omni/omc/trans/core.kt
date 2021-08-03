package io.xxx.omni.omc.trans

import com.alibaba.fastjson.JSON
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import io.xxx.omni.omc.model.CommittedOffset
import io.xxx.omni.omc.model.CommittedOffsetMapper
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.AcknowledgingMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

abstract class Translator : AcknowledgingMessageListener<String, String> {

    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    protected lateinit var documentMapper: DocumentMapper

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
        val document = getDocument(json.getString("sid"), json.getString("sn"))
        if (document == null) {
            log.error("报文[${record.value()}]不存在")
            return
        }

        val data = JSON.parseObject(document.data!!)
        val tid = data.getString("tid")
        val receiverPhone = data.getString("receiver_phone")
        val receiverState = data.getString("receiver_state")
        val receiverCity = data.getString("receiver_city")
        val receiverDistrict = data.getString("receiver_district")
        val receiverAddress = data.getString("receiver_address")
        println("==>> $tid : $receiverPhone - $receiverState - $receiverCity - $receiverDistrict - $receiverAddress")

        val committedOffset = CommittedOffset(record.topic(), record.partition(), record.offset())
        committedOffsetMapper.insert(committedOffset)
        acknowledgment.acknowledge()
    }
}