package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toJSONString
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.util.*

/**
 * 抖音
 *
 * https://op.jinritemai.com/docs/guide-docs/10/23
 */
abstract class DyPorter : Porter() {

    private val httpUrl = "https://openapi-fxg.jinritemai.com"

    protected abstract val method: String

    override val startPage = 0

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("update_time") to false

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(store, startTime, endTime, parameter)
        return response.getLong("total")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(store, startTime, endTime, parameter)
        return response.getJSONArray("list").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            buildDocument(value)
        }
    }

    protected abstract fun buildDocument(value: JSONObject): Document

    protected fun getResponse(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): JSONObject {
        val params = buildRequest(startTime, endTime, parameter, pageNo)
        val request = TreeMap(params)
        request["method"] = method
        request["app_key"] = store.appKey
        request["param_json"] = params.toJSONString()
        request["timestamp"] = LocalDateTime.now().format(dateTimeFormatter)
        request["v"] = "2"
        val builder = StringBuilder()
            .append(store.appSecret)
            .append(request.entries.joinToString("") { it.key + it.value })
            .append(store.appSecret)
        request["sign"] = DigestUtils.md5Hex(builder.toString())

        val urlBuilder = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .path(method.replace(".", "/"))
        request.forEach { urlBuilder.queryParam(it.key, it.value) }
        val response = restTemplate.getForEntity(urlBuilder.toUriString(), JSONObject::class.java).body!!
        if (response.getInteger("err_no") != 0) {
            throwException(method, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    private fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): Map<String, Any> {
        return TreeMap(
            mapOf(
                "is_desc" to "1",
                "order_by" to parameter.toString(),
                "start_time" to startTime.format(dateTimeFormatter),
                "end_time" to endTime.format(dateTimeFormatter),
                "page" to pageNo,
                "size" to pageSize,
            )
        )
    }
}

// 订单
open class DyTradePorter : DyPorter() {

    // https://op.jinritemai.com/docs/api-docs/15/55
    override val method = "order.list"

    override val documentType = DocumentType.TRADE

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("order_id"),
            value.toJSONString(),
            value.getString("update_time").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class DyTradeProcurator : DyTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("create_time") to false
}

// 退单
open class DyRefundPorter : DyPorter() {

    // https://op.jinritemai.com/docs/api-docs/17/80
    override val method = "refund.orderList"

    override val documentType = DocumentType.REFUND

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("aftersale_id"),
            value.getString("order_id"),
            value.toJSONString(),
            value.getString("update_time").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class DyRefundProcurator : DyRefundPorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("apply_time") to false
}