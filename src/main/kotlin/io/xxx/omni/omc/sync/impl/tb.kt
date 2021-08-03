package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toJSONObject
import org.springframework.web.util.UriComponentsBuilder
import java.lang.RuntimeException
import java.time.Duration
import java.time.LocalDateTime

/**
 * 淘宝
 *
 * https://open.taobao.com/docV3.htm?docId=73&docType=1
 */
abstract class TbPorter : Porter() {

    override val pageSize = 5_000

    protected abstract val path: String

    override val duration: Duration = Duration.ofDays(1)

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long {
        val response = getResponse(store, startTime, endTime)
        return response.getLong("count")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(store, startTime, endTime, pageNo, false)
        val data = response.getJSONArray("data")
        return data.map {
            val jsonObject = it.toJSONObject()
            buildDocument(jsonObject)
        }
    }

    protected abstract fun buildDocument(jsonObject: JSONObject): Document

    protected fun getResponse(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 0,
        counted: Boolean = true,
    ): JSONObject {
        val platform = omniProperties.getPlatform(store.pid) ?: throw RuntimeException("接口地址信息不存在")
        val url = UriComponentsBuilder.fromHttpUrl(platform.httpUrl!!)
            .path(path)
            .queryParam("sellerNick", store.oid)
            .queryParam("startTime", startTime.format(dateTimeFormatter))
            .queryParam("endTime", endTime.format(dateTimeFormatter))
            .queryParam("pageNo", pageNo)
            .queryParam("pageSize", pageSize)
            .queryParam("counted", counted)
            .build(false)
            .toUriString()
        return restTemplate.getForEntity(url, JSONObject::class.java).body!!
    }
}

// 订单
@Suppress("unused")
class TbTradePorter : TbPorter() {

    override val path = "/trades/page"

    override val documentType = DocumentType.TRADE

    override fun buildDocument(jsonObject: JSONObject): Document {
        return Document(
            jsonObject.getString("tid"),
            jsonObject.getJSONObject("jdp_response")
                .getJSONObject("trade_fullinfo_get_response")
                .getJSONObject("trade"),
            LocalDateTime.parse(jsonObject.getString("jdp_modified"))
        )
    }
}

// 退单
@Suppress("unused")
class TbRefundPorter : TbPorter() {

    override val path = "/refunds/page"

    override val documentType = DocumentType.REFUND

    override fun buildDocument(jsonObject: JSONObject): Document {
        return Document(
            jsonObject.getString("refund_id"),
            jsonObject.getString("tid"),
            jsonObject.getJSONObject("jdp_response")
                .getJSONObject("refund_get_response")
                .getJSONObject("refund"),
            LocalDateTime.parse(jsonObject.getString("jdp_modified"))
        )
    }
}