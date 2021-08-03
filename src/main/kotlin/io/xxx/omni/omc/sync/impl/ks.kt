package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.pool
import io.xxx.omni.omc.util.toJSONString
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 快手
 */
abstract class KsPorter : Porter() {

    private val httpUrl = "https://open.kwaixiaodian.com"

    override val duration: Duration = Duration.ofDays(1)

    protected abstract val method: String

    override fun pullAndSave(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?
    ) {
        var cursor = ""
        do {
            val request = TreeMap<String, String>()
            request["appkey"] = store.appKey!!
            request["method"] = method
            request["version"] = "1"
            request["param"] = buildRequest(store, startTime, endTime, cursor).toJSONString()
            request["access_token"] = store.accessToken!!
            request["timestamp"] = System.currentTimeMillis().toString()
            request["signMethod"] = "MD5"
            val builder = StringBuilder()
                .append(request.entries.joinToString("&"))
                .append("&").append("signSecret").append("=").append(store.appSecret)
            val sign = DigestUtils.md5Hex(builder.toString())
            val urlBuilder = UriComponentsBuilder.fromHttpUrl(httpUrl)
                .path(method.replace(".", "/"))
                .queryParam("sign", sign)
            request.forEach { urlBuilder.queryParam(it.key, it.value) }
            val url = urlBuilder.build(false).toUriString()
            val response = restTemplate.getForEntity(url, JSONObject::class.java).body!!
            if (response.getIntValue("result") != 1) {
                throwException(method, response.toJSONString())
            }
            val (documents, nextCursor) = buildDocuments(response)
            pool.submit { save(store, documents) }
            cursor = nextCursor
        } while (cursor != "nomore")
    }

    protected open fun buildRequest(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        cursor: String
    ): Map<String, String> {
        val request = hashMapOf<String, String>()
        request["pageSize"] = pageSize.toString()
        request["type"] = "1"
        request["sort"] = "1"
        request["queryType"] = "2"
        request["beginTime"] = startTime.toInstant(zoneOffset).toEpochMilli().toString()
        request["endTime"] = endTime.toInstant(zoneOffset).toEpochMilli().toString()
        request["pcursor"] = cursor
        return request
    }

    protected abstract fun buildDocuments(response: JSONObject): Pair<List<Document>, String>
}

// 订单
@Suppress("unused")
class KsTradePorter : KsPorter() {

    override val method = "open.seller.order.pcursor.list"

    override val documentType = DocumentType.TRADE

    override fun buildRequest(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        cursor: String
    ): Map<String, String> {
        val request = super.buildRequest(store, startTime, endTime, cursor).toMutableMap()
        request["cpsType"] = "0"
        return request
    }

    override fun buildDocuments(response: JSONObject): Pair<List<Document>, String> {
        val data = response.getJSONObject("data")
        val documents = data.getJSONArray("orderInfoList")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                val modified = value.getLong("updateTime").toLocalDateTime()
                Document(value.getString("oid"), value.toJSONString(), modified)
            }
        return documents to data.getString("pcursor")
    }
}

// 退单
@Suppress("unused")
class KsRefundPorter : KsPorter() {

    override val method = "open.seller.order.refund.pcursor.list"

    override val documentType = DocumentType.REFUND

    override fun buildRequest(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        cursor: String
    ): Map<String, String> {
        val request = super.buildRequest(store, startTime, endTime, cursor).toMutableMap()
        request["negotiateStatus"] = "0"
        return request
    }

    override fun buildDocuments(response: JSONObject): Pair<List<Document>, String> {
        val data = response.getJSONObject("data")
        val documents = data.getJSONArray("refundOrderInfoList")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                val modified = value.getLong("updateTime").toLocalDateTime()
                Document(value.getString("refundId"), value.getString("oid"), value.toJSONString(), modified)
            }
        return documents to data.getString("pcursor")
    }
}