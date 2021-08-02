package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.toLocalDateTime
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime


/**
 * POS
 */
@Suppress("unused")
class PosPorter : Porter() {

    private val httpUrl = "http://POS-SERVER/orders/data"

    private fun getResponse(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): JSONObject {
        val url = UriComponentsBuilder.fromHttpUrl(httpUrl)
            .queryParam("startTime", startTime.format(dateTimeFormatter))
            .queryParam("endTime", endTime.format(dateTimeFormatter))
            .queryParam("pageNo", pageNo)
            .queryParam("pageSize", pageSize)
            .toUriString()

        val response = lbRestTemplate.getForObject(url, JSONObject::class.java)
        if (response!!.getIntValue("code") != 0) {
            throwException(url, response.toJSONString())
        }
        return response
    }

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        return getResponse(startTime, endTime).getLong("data")
    }

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long
    ): List<Document> {
        val data = getResponse(startTime, endTime).getJSONArray("data")
        return data.map {
            @Suppress("unchecked_cast")
            val value = JSONObject(it as Map<String, Any>)
            Document(
                value.getString("id"),
                value.getString("original_order_id"),
                value,
                value.getString("updated_time").toLocalDateTime()
            )
        }
    }
}