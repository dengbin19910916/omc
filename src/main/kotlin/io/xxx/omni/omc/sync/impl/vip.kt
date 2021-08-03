package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.util.*

/**
 * 唯品会
 *
 * https://vop.vip.com/doccenter/viewdoc/8
 */
abstract class VipPorter : Porter() {

    private val httpUrl = "https://vop.vipapis.com"

    protected abstract val method: String

    override val pageSize = 200

    protected fun getResponse(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1,
    ): JSONObject {
        val params = buildRequest(startTime, endTime, pageNo)
        val request = TreeMap(params)
        request["service"] = "vipapis.marketplace.delivery.SovDeliveryService"
        request["method"] = method
        request["version"] = "1.0.0"
        request["timestamp"] = LocalDateTime.now().toEpochSecond(zoneOffset)
        request["format"] = "json"
        request["appKey"] = store.appKey
        request["accessToken"] = store.accessToken
        val sign = HmacUtils(HmacAlgorithms.HMAC_MD5, store.appSecret)
            .hmacHex(request.entries.joinToString("") { it.key + it.value })

        val urlBuilder = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .queryParam("sign", sign)
        request.forEach { urlBuilder.queryParam(it.key, it.value) }

        val headers = HttpHeaders()
        headers["Content-Type"] = "application/json"
        val entity = HttpEntity<Any>(params, headers)
        return restTemplate.exchange(
            urlBuilder.toUriString(), HttpMethod.POST,
            entity, JSONObject::class.java
        ).body!!
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1,
    ): Map<String, Any>
}

// 订单
// https://vop.vip.com/home#/api/method/detail/vipapis.marketplace.delivery.SovDeliveryService-1.0.0/getOrders
@Suppress("unused")
open class VipTradePorter : VipPorter() {

    override val method = "getOrders"

    override val documentType = DocumentType.TRADE

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(store, startTime, endTime)
        return response.getLong("total")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(store, startTime, endTime)
        return response.getJSONArray("orders").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("order_id"),
                value.toJSONString(),
                value.getString("last_update_time").toLocalDateTime()
            )
        }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long,
    ): Map<String, Any> {
        return TreeMap(
            mapOf(
                "query_start_time" to startTime.format(dateTimeFormatter),
                "query_end_time" to endTime.format(dateTimeFormatter),
                "page" to pageNo,
                "limit" to pageSize,
            )
        )
    }
}


// 退单
// https://vop.vip.com/home#/api/method/detail/vipapis.marketplace.delivery.SovDeliveryService-1.0.0/batchGetCancelInfo
@Suppress("unused")
open class VipRefundPorter : VipPorter() {

    override val method = "batchGetCancelInfo"

    override val documentType = DocumentType.REFUND

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(store, startTime, endTime)
        return response.getLong("total")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(store, startTime, endTime)
        return response.getJSONArray("cancelInfos").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("apply_sn"),
                value.getString("order_id"),
                value.toJSONString(),
                value.getString("last_update_time").toLocalDateTime()
            )
        }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long,
    ): Map<String, Any> {
        return TreeMap(
            mapOf(
                "start_time" to startTime.format(dateTimeFormatter),
                "end_time" to endTime.format(dateTimeFormatter),
                "page" to pageNo,
                "limit" to pageSize,
            )
        )
    }
}