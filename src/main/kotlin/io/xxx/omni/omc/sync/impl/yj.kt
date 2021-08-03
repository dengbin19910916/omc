package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toJSONString
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 云集
 */
abstract class YjPorter : Porter() {

    private val httpUrl = "https://op.yunjiglobal.com/opgateway/api/openapi"

    protected abstract val method: String

    override val duration: Duration = Duration.ofDays(1)

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(1) to false

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1
    ): JSONObject {
        val request = buildRequest(startTime, endTime, parameter as Int, pageNo)
        val signMap = TreeMap<String, String>()
        signMap["method"] = method
        signMap["timestamp"] = LocalDateTime.now().format(dateTimeFormatter)
        signMap["format"] = "json"
        signMap["app_key"] = store.appKey!!
        signMap["v"] = "3.0"
        signMap["sign_method"] = "md5"
        signMap["session"] = store.accessToken!!
        val builder = StringBuilder()
            .append(store.appSecret)
            .append(signMap.entries.joinToString("") { it.key + it.value })
            .append(request.toJSONString())
            .append(store.appSecret)
        val sign = DigestUtils.md5Hex(builder.toString()).uppercase()

        val urlBuilder = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .queryParam("sign", sign)
        signMap.forEach { urlBuilder.queryParam(it.key, it.value) }
        val url = urlBuilder.build(false).toUri()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val requestEntity = HttpEntity<Any>(request, headers)
        val response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, JSONObject::class.java).body!!

        if (response.getString("flag") == "failure") {
            throwException(method, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    private fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        queryType: Int,
        pageNo: Long = 1
    ): Map<String, Any> {
        return mapOf(
            "query_type" to queryType.toString(),
            "start_modified" to startTime.format(dateTimeFormatter),
            "end_modified" to endTime.format(dateTimeFormatter),
            "page_no" to pageNo.toString(),
            "page_size" to pageSize.toString(),
        )
    }
}

// 订单
@Suppress("unused")
open class YjTradePorter : YjPorter() {

    override val method = "pop.order.list.get"

    override val documentType = DocumentType.TRADE

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getLong("total")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        return response.getJSONArray("lists")?.map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("order_id"),
                value.toJSONString(),
                value.getLong("modify_time").toLocalDateTime()
            )
        }
    }
}

@Suppress("unused")
class YjTradeProcurator : YjTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(0) to false
}

// 退单
@Suppress("unused")
open class YjRefundPorter : YjPorter() {

    override val method = "pop.refund.list.get"

    override val documentType = DocumentType.REFUND

    override val duration: Duration = Duration.ofHours(12)

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getLong("total_results")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        return response.getJSONArray("refunds")?.map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("refund_id"),
                value.getString("order_id"),
                value.toJSONString(),
                value.getLong("modify_time").toLocalDateTime()
            )
        }
    }
}

@Suppress("unused")
class YjRefundProcurator : YjRefundPorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf(0) to false
}