package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.util.*

/**
 * 小红书
 *
 * https://school.xiaohongshu.com/open/quick-start/sign.html
 */
abstract class XhsPorter : Porter() {

    private val httpUrl = "https://ark.xiaohongshu.com"

    protected abstract val path: String

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter)
        return response.getLong("total_number")
    }


    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1
    ): JSONObject {
        val request = buildRequest(startTime, endTime, parameter, pageNo)
        val signMap = TreeMap(request)
        signMap["timestamp"] = LocalDateTime.now().toEpochSecond(zoneOffset)
        signMap["app-key"] = store
        val needSign = "${path}?${signMap.entries.joinToString("&") { "${it.key}${it.value}" }}${store.appSecret}"
        val sign = DigestUtils.md5Hex(needSign)

        val builder = UriComponentsBuilder.fromHttpUrl(httpUrl)
            .path(path)
            .queryParam("sign", sign)
        signMap.forEach { builder.queryParam(it.key, it.value) }
        val response = restTemplate.getForEntity(builder.toUriString(), JSONObject::class.java).body!!
        if (response.getString("error_code") != "0") {
            throwException(path, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): Map<String, Any>
}

// 订单
// https://school.xiaohongshu.com/open/package/packages-list.html
@Suppress("unused")
class XhsTradePorter : XhsPorter() {

    override val path = "/ark/open_api/v0/packages"

    override val documentType = DocumentType.TRADE

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val response = getResponse(startTime, endTime, parameter)
        return response.getJSONArray("package_list")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                Document(
                    value.getString("package_id"),
                    value.toJSONString(),
                    value.getLong("time").toLocalDateTime()
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
            "page_no" to pageNo,
            "page_size" to pageSize,
            "start_time" to startTime.format(dateTimeFormatter),
            "end_time" to endTime.format(dateTimeFormatter),
            "time_type" to "updated_at",    // 订单创建时间：created_at，订单确认时间：confirmed_at，订单更新时间：updated_at
        )
    }
}


// 退单
// https://school.xiaohongshu.com/open/refund/refund-list.html
@Suppress("unused")
class XhsRefundPorter : XhsPorter() {

    override val path = "/ark/open_api/v0/refund/list"

    override val documentType = DocumentType.REFUND

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val response = getResponse(startTime, endTime, parameter)
        return response.getJSONArray("package_list")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                Document(
                    value.getString("returns_id"),
                    value.getString("package_id"),
                    value.toJSONString(),
                    value.getLong("time").toLocalDateTime()
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
            "start_time" to startTime.format(dateTimeFormatter),
            "end_time" to endTime.format(dateTimeFormatter),
            "page" to pageNo,
            "page_size" to pageSize,
        )
    }
}