package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toJSONString
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.commons.codec.digest.DigestUtils
import java.time.LocalDateTime
import java.util.*

/**
 * 丁香医生
 */
abstract class DxYsPorter : Porter() {

    private val httpUrl = "https://mama.dxy.com/japi/platform/201029020"

    protected abstract val method: String

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): JSONObject {
        val request = TreeMap(buildRequest(startTime, endTime, pageNo))
        val paramsJson = request.toJSONString()
        request["method"] = method
        request["bizcontent"] = paramsJson
        request["appkey"] = store.appKey!!
        request["token"] = store.accessToken!!
        val builder = StringBuilder()
            .append(store.appSecret)
            .append(request.entries.joinToString("") { it.key + it.value })
            .append(store.appSecret)
        val sign = DigestUtils.md5Hex(builder.toString())
        request["sign"] = sign
        val response = restTemplate.postForEntity(httpUrl, request, JSONObject::class.java).body!!
        if (!response.getString("code").equals("0")) {
            throwException(method, response.toJSONString())
        }
        return response
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): Map<String, Any>
}

@Suppress("unused")
class DxYsTradePorter : DxYsPorter() {

    override val method = "Differ.JH.Business.GetOrder"

    override val documentType = DocumentType.TRADE

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(startTime, endTime)
        return response.getLong("numtotalorder")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(startTime, endTime, pageNo)
        return response.getJSONArray("orders").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("PlatOrderNo"),
                value,
                value.getString("tradetime").toLocalDateTime()
            )
        }
    }

    override fun buildRequest(startTime: LocalDateTime, endTime: LocalDateTime, pageNo: Long): Map<String, Any> {
        return mapOf(
            "OrderStatus" to "JH_99",
            "TimeType" to "JH_01",
            "StartTime" to startTime.format(dateTimeFormatter),
            "EndTime" to endTime.format(dateTimeFormatter),
            "PageIndex" to pageNo.toString(),
            "PageSize" to pageSize.toString(),
        )
    }
}