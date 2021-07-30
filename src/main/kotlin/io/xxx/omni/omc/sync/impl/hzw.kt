package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toJSONString
import io.xxx.omni.omc.util.toLocalDateTime
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

/**
 * 孩子王
 */
abstract class HzwPorter : Porter() {

    private val httpUrl = "http://dapopen.haiziwang.com/pop/"

    protected abstract val path: String

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(2) to false

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getLong("totalNum")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getJSONArray("data").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            buildDocument(value)
        }
    }

    protected abstract fun buildDocument(value: JSONObject): Document

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): JSONObject {
        val url = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .path(path)
            .queryParam("appkey", store.appKey)
            .queryParam("loginName", store.accessToken)
            .queryParam("jsonStr", buildRequest(startTime, endTime, parameter, pageNo).toJSONString())
            .toUriString()
        val response = restTemplate.getForEntity(url, JSONObject::class.java).body!!
        if (response.getString("errorCode") != "0") {
            throwException(path, response.toJSONString())
        }
        return response
    }

    private fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeParamType: Any?, // 1. 创建时间，2. 更新时间
        pageNo: Long = 1,
    ): Map<String, String> {
        return mapOf(
            "startTime" to startTime.format(dateTimeFormatter),
            "endTime" to endTime.format(dateTimeFormatter),
            "page" to pageNo.toString(),
            "pageSize" to pageSize.toString(),
            "timeParamType" to timeParamType.toString()
        )
    }
}

// 订单
open class HzwTradePorter : HzwPorter() {

    override val path = "order.list"

    override val documentType = DocumentType.TRADE

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("dealCode"),
            value,
            value.getLong("LastUpdateTime").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class HzwTradeProcurator : HzwTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(1) to false
}

// 退单
open class HzwRefundPorter : HzwPorter() {

    override val path = "refund.orderList"

    override val documentType = DocumentType.REFUND

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("dealCode"),
            value.getString("dealCode"),
            value,
            value.getString("update_time").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class HzwRefundProcurator : HzwRefundPorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(1) to false
}