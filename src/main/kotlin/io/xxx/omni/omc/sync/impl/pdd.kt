package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 拼多多
 */
@Suppress("SpringJavaAutowiredMembersInspection")
abstract class PddPorter : Porter() {

    private val httpUrl = "https://gw-api.pinduoduo.com/api/router"

    protected abstract val type: String

    override val pageSize = 100

    override val duration: Duration = Duration.ofMinutes(30)

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): JSONObject {
        val request = buildRequest(startTime, endTime, parameter, pageNo)
        val signMap = TreeMap(request)
        signMap["type"] = type
        signMap["client_id"] = store.appKey
        signMap["timestamp"] = LocalDateTime.now().toEpochSecond(zoneOffset)
        signMap["data_type"] = "JSON"
        signMap["access_token"] = store.accessToken
        val builder = StringBuilder()
            .append(store.appSecret)
            .append(signMap.entries.joinToString("") { it.key + it.value })
            .append(store.appSecret)
        signMap["sign"] = DigestUtils.md5Hex(builder.toString()).uppercase()

        val headers = HttpHeaders()
        headers["Content-Type"] = "application/json"
        val entity = HttpEntity(signMap, headers)
        val response = restTemplate.exchange(httpUrl, HttpMethod.POST, entity, JSONObject::class.java).body!!

        val errorResponse = response.getJSONObject("error_response")
        if (errorResponse != null) {
            throwException(type, errorResponse.toJSONString())
        }
        return response
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1
    ): Map<String, Any>
}

// 订单 https://open.pinduoduo.com/application/document/api?id=pdd.order.number.list.increment.get
@Suppress("unused")
class PddTradePorter : PddPorter() {

    override val delay: Duration = Duration.ofMinutes(3)

    override val type = "pdd.order.number.list.increment.get"

    override val documentType = DocumentType.TRADE

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getJSONObject("order_sn_increment_get_response")
            .getLong("total_count")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        return response.getJSONObject("order_sn_increment_get_response")
            .getJSONArray("order_sn_list")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                Document(
                    value.getString("order_sn"),
                    value,
                    value.getLong("updated_at").toLocalDateTime()
                )
            }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): Map<String, Any> {
        return mapOf(
            "is_lucky_flag" to "0",
            "order_status" to "5",
            "start_updated_at" to startTime.toEpochSecond(zoneOffset),
            "end_updated_at" to endTime.toEpochSecond(zoneOffset),
            "page" to pageNo,
            "page_size" to pageSize,
        )
    }
}


// 订单 https://open.pinduoduo.com/application/document/api?id=pdd.refund.list.increment.get
@Suppress("unused")
class PddRefundPorter : PddPorter() {

    override val type = "pdd.refund.list.increment.get"

    override val documentType = DocumentType.REFUND

    override fun getParameters(): Pair<List<Any?>, Boolean> = listOf(
        0, 2, 3, 4, 5, 6, 7, 8, 9,
        10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 22, 24, 25, 27,
        31, 32
    ) to true

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getJSONObject("refund_increment_get_response")
            .getLong("total_count")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        return response.getJSONObject("refund_increment_get_response")
            .getJSONArray("refund_list")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                Document(
                    value.getString("id"),
                    value.getString("order_sn"),
                    value,
                    value.getLong("updated_time").toLocalDateTime()
                )
            }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): Map<String, Any> {
        return mapOf(
            "after_sales_status" to parameter.toString().toInt(),
            "after_sales_type" to 1,
            "start_updated_at" to startTime.toEpochSecond(zoneOffset),
            "end_updated_at" to endTime.toEpochSecond(zoneOffset),
            "page" to pageNo,
            "page_size" to pageSize,
        )
    }
}