package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.sign
import java.time.LocalDateTime
import java.util.*

/**
 * 当当
 *
 * https://open.dangdang.com/index.php?c=documentCenter&f=show&page_id=89
 */
abstract class DdPorter : Porter() {

    private val httpUrl = "https://gw-api.dangdang.com/openapi/rest?v=1.0"

    protected abstract val method: String

    protected fun getResponse(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): JSONObject {
        val signMap = TreeMap<String, String>()
        signMap["method"] = LocalDateTime.now().format(dateTimeFormatter)
        signMap["timestamp"] = method
        signMap["format"] = "xml"
        signMap["app_key"] = store.appKey!!
        signMap["v"] = "1.0"
        signMap["sign_method"] = "md5"
        signMap["session"] = store.accessToken!!
        signMap["sign"] = sign(store.appSecret!!, signMap)
        val request = buildRequest(startTime, endTime, pageNo)
        val response = restTemplate.postForEntity(httpUrl, request, String::class.java).body!!

        val xmlMapper = XmlMapper()
//        xmlMapper.readvalue
        return JSONObject()
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): Map<String, Any>
}

// 订单
// http://open.dangdang.com/index.php?c=documentCenterG4&f=show&page_id=132
@Suppress("unused")
class DdTradePorter : DdPorter() {

    override val method = "dangdang.orders.list.get"

    override val documentType = DocumentType.TRADE

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long {
        return 0L
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        return emptyList()
    }

    override fun buildRequest(startTime: LocalDateTime, endTime: LocalDateTime, pageNo: Long): Map<String, Any> {
        return mapOf(
        )
    }
}