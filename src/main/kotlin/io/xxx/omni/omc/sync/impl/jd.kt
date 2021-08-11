package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatPattern
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toLocalDateTime
import okhttp3.Request
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

/**
 * 京东
 *
 * https://open.jd.com/home/home#/doc/common
 */
abstract class JdPorter : Porter() {

    private val httpUrl = "http://114.67.174.95/routerjson"

    protected abstract val method: String

    // 京东接口允许最大的页码为300，为了减少页码超过300的可能性
    override val duration: Duration = Duration.ofDays(1)

    protected fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): JSONObject {
        val request = buildRequest(startTime, endTime, parameter, pageNo)
        val signMap = TreeMap<String, String>()
        signMap["method"] = method
        signMap["access_token"] = store.accessToken!!
        signMap["app_key"] = store.appKey!!
        signMap["timestamp"] = OffsetDateTime.now().format(dateTimeFormatter)
        signMap["360buy_param_json"] = JSON.toJSONStringWithDateFormat(request, dateTimeFormatPattern)
        signMap["v"] = "2.0"
        val builder = StringBuilder()
            .append(store.appSecret)
            .append(signMap.entries.joinToString("") { it.key + it.value })
            .append(store.appSecret)
        val sign = DigestUtils.md5Hex(builder.toString()).uppercase()

        val urlBuilder = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
        signMap.forEach { urlBuilder.queryParam(it.key, it.value) }
        urlBuilder.queryParam("sign", sign)
        val url = urlBuilder.build(false).toUriString()
        val requestBuilder = Request.Builder()
            .url(url)
        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body()
        if (body == null) {
            throwException(method, "响应结果为空")
        }
        val result = JSON.parseObject(body!!.string())
        if (result.getString("code") != null) {
            throwException(method, result.toJSONString())
        }
        return result
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): Map<String, Any>
}

// 订单
@Suppress("unused")
class JdTradePorter : JdPorter() {

    override val method = "jingdong.pop.order.search"

    override val documentType = DocumentType.TRADE

    override fun getParameters(): Pair<List<Any?>, Boolean> = listOf(0) to false

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val result = getResult(startTime, endTime, parameter)
        return result.getLong("orderTotal")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val result = getResult(startTime, endTime, parameter, pageNo)
        return result.getJSONArray("orderInfoList").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("orderId"),
                value.toJSONString(),
                value.getString("modified").toLocalDateTime()
            )
        }
    }

    private fun getResult(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): JSONObject {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        if (response.containsKey("error_response")) {
            throwException(method, response.toJSONString())
        }
        val result = response.getJSONObject("jingdong_pop_order_search_responce")
            .getJSONObject("searchorderinfo_result")
        val apiResult = result.getJSONObject("apiResult")
        if (!apiResult.getBoolean("success")) {
            throwException(method, response.toJSONString())
        }
        return result
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): Map<String, Any> {
        return mapOf(
            "sortType" to 1,
            "dateType" to 0,
            "start_date" to startTime.format(dateTimeFormatter),
            "end_date" to endTime.format(dateTimeFormatter),
            "page" to pageNo,
            "page_size" to pageSize,
            "optional_fields" to optionalFieldsDesc,
            "order_state" to orderStateDesc,
        )
    }

    companion object {
        private val optionalFields = listOf(
            "orderSellerPrice", "orderType", "logisticsId", "orderSign", "orderId",
            "paymentConfirmTime", "orderStateRemark", "orderState", "payType", "itemInfoList",
            "pin", "waybill", "customc", "modified", "salesPin",
            "freightPrice", "tuiHuoWuYou", "balanceUsed", "serviceFee", "directParentOrderId",
            "originalConsigneeInfo", "orderTotalPrice", "invoiceCode", "mdbStoreId", "idSopShipmenttype",
            "sellerDiscount", "customcModel", "venderRemark", "couponDetailList", "menDianId",
            "venderId", "orderRemark", "orderMarkDesc", "scDT", "parentOrderId",
            "orderEndTime", "taxFee", "vatInfo", "open_id_buyer", "open_id_seller",
            "invoiceInfo", "orderSource", "orderExt", "deliveryType", "pauseBizInfo",
            "storeId", "orderPayment", "consigneeInfo", "returnOrder", "invoiceEasyInfo",
            "storeOrder", "realPin", "orderStartTime",
        )

        private val optionalFieldsDesc = optionalFields.joinToString(",")

        private val orderState = listOf(
            "WAIT_SELLER_STOCK_OUT", "WAIT_GOODS_RECEIVE_CONFIRM", "WAIT_SELLER_DELIVERY", "PAUSE", "FINISHED_L",
            "TRADE_CANCELED", "LOCKED", "POP_ORDER_PAUSE"
        )

        private val orderStateDesc = orderState.joinToString(",")
    }
}

// 退单
@Suppress("unused")
class JdRefundPorter : JdPorter() {

    override val method = "jingdong.pop.afs.soa.refundapply.queryPageList"

    override val pageSize = 50

    override val documentType = DocumentType.REFUND

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val result = getResult(startTime, endTime, parameter)
        return result.getLong("orderTotal")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val result = getResult(startTime, endTime, parameter, pageNo)
        return result.getJSONArray("result").map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("id"),
                value.getString("orderId"),
                value.toJSONString(),
                value.getString("checkTime").toLocalDateTime() ?: value.getString("applyTime").toLocalDateTime()
            )
        }
    }

    private fun getResult(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long = 1,
    ): JSONObject {
        val response = getResponse(startTime, endTime, parameter, pageNo)
        val result = response.getJSONObject("jingdong_pop_afs_soa_refundapply_queryPageList_responce")
            .getJSONObject("queryResult")
        if (!result.getBoolean("success")) {
            throwException(method, response.toJSONString())
        }
        return result
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): Map<String, Any> {
        return mapOf(
            "applyTimeStart" to startTime.format(dateTimeFormatter),
            "applyTimeEnd" to endTime.format(dateTimeFormatter),
            "pageIndex" to pageNo,
            "pageSize" to pageSize,
        )
    }
}