package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import io.xxx.omni.omc.model.*
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.util.*

/**
 * 爱库存
 *
 * https://open.aikucun.com/delivery
 */
@Suppress("SpringJavaAutowiredMembersInspection")
abstract class AkcPorter : Porter() {

    @Autowired
    protected lateinit var akcActivityMapper: AkcActivityMapper

    private val httpUrl = "https://openapi.aikucun.com"

    protected abstract val path: String

    protected fun getResponse(store: Store, parameter: Any? = null, pageNo: Long = 1): JSONObject {
        val request = TreeMap(buildRequest(parameter, pageNo))
        request["appid"] = store.appKey!!
        request["noncestr"] = "1"
        request["erp"] = "Winner-ERP"
        request["erpversion"] = "1.0"
        request["timestamp"] = System.currentTimeMillis().toString()
        val urlBuilder = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .path(path)
        request.forEach { urlBuilder.queryParam(it.key, it.value) }
        request["appsecret"] = store.appSecret!!
        val sign = DigestUtils.sha1Hex(request.entries.joinToString("&"))
        urlBuilder.queryParam("sign", sign)
        val url = urlBuilder.build(false).toUriString()
        val response = restTemplate.getForEntity(url, JSONObject::class.java).body!!
        if (response.getString("status") != "success") {
            throwException(path, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    protected abstract fun buildRequest(parameter: Any?, pageNo: Long = 1): Map<String, Any>
}

// 活动
@Suppress("unused")
class AkcActivityPorter : AkcPorter() {

    override val path = "/api/v2/activity/list"

    override fun pullAndSave(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?
    ) {
        val response = getResponse(store)
        val array = response.getJSONArray("list")
        array.forEach {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            val activity = AkcActivity(store, value)
            val oldActivity = akcActivityMapper.selectById(activity.id)
            if (oldActivity == null) {
                akcActivityMapper.insert(activity)
            } else if (oldActivity != activity) {
                akcActivityMapper.updateById(activity)
            }
        }
    }

    override fun buildRequest(parameter: Any?, pageNo: Long): Map<String, Any> {
        /*
        1 - 获取活动结束售后期的活动列表
        2 - 获取当前在线销售的活动列表
        9 - 同时获取在线销售的以及售后中的活动列表
        */
        return mapOf("status" to "9")
    }
}

// 订单
@Suppress("unused")
class AkcTradePorter : AkcPorter() {

    override val pageSize = 1000

    override val path = "/api/v2/order/listall"

    override val documentType = DocumentType.TRADE

    override fun getParameters(): Pair<List<Any>, Boolean> {
        val wrapper = KtQueryWrapper(AkcActivity::class.java)
            .eq(AkcActivity::sid, store.id)
        return akcActivityMapper.selectList(wrapper) to true
    }

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        if (parameter == null) {
            return 0
        }
        val response = getResponse(store, parameter)
        return response.getLong("totalrecord")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document>? {
        val response = getResponse(store, parameter, pageNo)
        return response.getJSONArray("list")?.map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            val modified = (value.getString("paytime") ?: value.getString("ordertime")).toLocalDateTime()
            Document(value.getString("adorderid"), value, modified)
        }
    }

    override fun buildRequest(parameter: Any?, pageNo: Long): Map<String, Any> {
        return mapOf(
            "activityid" to (parameter as AkcActivity).id!!,
            "page" to pageNo,
            "pagesize" to pageSize,
        )
    }
}