package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.sign
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import java.time.LocalDateTime
import java.util.*

/**
 * 贝贝
 */
abstract class BbPorter : Porter() {

    private val httpUrl = "http://api.open.beibei.com/outer_api/out_gateway/route.html"

    protected abstract val method: String

    override val pageSize = 300

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("modified_time") to false

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(store, startTime, endTime, parameter)
        return response.getLong("count")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(store, startTime, endTime, parameter, pageNo)
        return response.getJSONArray("data")?.map {
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
        val signMap = TreeMap(buildRequest(startTime, endTime, parameter.toString(), pageNo))
        signMap["method"] = method
        signMap["timestamp"] = LocalDateTime.now().toEpochSecond(zoneOffset).toString()
        signMap["app_id"] = store.appKey!!
        signMap["session"] = store.accessToken!!
        signMap["sign"] = sign(store.appSecret!!, signMap)
        val response = restTemplate.postForEntity(httpUrl, signMap, JSONObject::class.java).body!!
        val errCode = response.getString("err_code")
        if (errCode != null) {
            throwException(method, response.toJSONString())
        }
        return response
    }

    private fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        timeRange: String,
        pageNo: Long = 1
    ): Map<String, String> {
        return mapOf(
            "time_range" to timeRange,
            "start_time" to startTime.format(dateTimeFormatter),
            "end_time" to endTime.format(dateTimeFormatter),
            "page_no" to pageNo.toString(),
            "page_size" to pageSize.toString(),
        )
    }
}

// 订单
@Suppress("unused")
open class BbTradePorter : BbPorter() {

    override val method = "beibei.outer.trade.order.get"

    override val documentType = DocumentType.TRADE

    override fun getStartTime(): LocalDateTime {
        val minStartTime = LocalDateTime.now().minusDays(30).plusMinutes(1)
        return if (storeJob.endTime!! < minStartTime) minStartTime else storeJob.endTime!!
    }

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("oid"),
            value,
            value.getString("modified_time").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class BbTradeProcurator : BbTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("create_time") to false
}

// 退单
@Suppress("unused")
class BbRefundPorter : BbPorter() {

    override val method = "beibei.outer.refunds.get"

    override val documentType = DocumentType.REFUND

    override fun buildDocument(value: JSONObject): Document {
        return Document(
            value.getString("id"),
            value.getString("oid"),
            value,
            value.getString("modified_time").toLocalDateTime()
        )
    }
}

@Suppress("unused")
class BbRefundProcurator : BbTradePorter() {

    override val cron = "0/10 * * * * ?"

    override fun getParameters(): Pair<List<Any>, Boolean> = listOf("create_time") to false
}