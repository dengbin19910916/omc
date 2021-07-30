package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 宝宝树
 *
 * http://platform.meitun.com/apidoc/
 */
abstract class BbsPagePorter : Porter() {

    private val httpUrl = "http://platform.meitun.com"

    protected abstract val path: String

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageSize: Int,
        pageNo: Long = 1
    ): JSONObject {
        val timestamp = LocalDateTime.now().format(formatter)
        val apiVersion = "v6.0"
        val sign = DigestUtils.md5Hex(store.appKey + store.accessToken + timestamp + apiVersion).uppercase()
        val url = UriComponentsBuilder.fromHttpUrl(httpUrl)
            .path(path)
            .queryParam("appkey", store.appKey)
            .queryParam("version", apiVersion)
            .queryParam("timestamp", timestamp)
            .queryParam("sign", sign)
            .toUriString()
        val request = buildRequest(startTime, endTime, parameter as String, pageSize, pageNo)
        val response = restTemplate.postForEntity(url, request, JSONObject::class.java).body!!
        if (!response.getBoolean("isSuccess")) {
            throwException(path, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeRange: String,
        pageSize: Int,
        pageNo: Long = 1
    ): Map<String, Any>

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

@Suppress("unused")
open class BbsTradePorter : BbsPagePorter() {

    override val path = "/order/orderInPage"

    override val documentType = DocumentType.TRADE

    override fun getParameters(): Pair<List<Any>, Boolean> {
        return listOf("modified_time") to false
    }

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime, parameter, pageSize)
        return response.getLong("totalCount")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(startTime, endTime, parameter, pageSize, pageNo)
        val list = response.getJSONArray("list")
        return list.map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            val modified = value.getLong("payTime").toLocalDateTime() ?: value.getLong("createTime").toLocalDateTime()
            Document(value.getString("code"), value, modified)
        }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeRange: String,
        pageSize: Int,
        pageNo: Long
    ): Map<String, Any> {
        return mapOf(
            "timeRange" to timeRange,
            "startTime" to startTime.format(dateTimeFormatter),
            "endTime" to endTime.format(dateTimeFormatter),
            "pageNo" to pageNo,
            "pageSize" to pageSize,
        )
    }
}

@Suppress("unused")
class BbsTradeProcurator : BbsTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("create_time") to false
}