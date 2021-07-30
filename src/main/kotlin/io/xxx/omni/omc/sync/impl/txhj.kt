package io.xxx.omni.omc.sync.impl

import com.alibaba.fastjson.JSONObject
import com.google.common.util.concurrent.RateLimiter
import io.xxx.omni.omc.model.Document
import io.xxx.omni.omc.model.DocumentType
import io.xxx.omni.omc.model.Store
import io.xxx.omni.omc.sync.Porter
import io.xxx.omni.omc.util.toLocalDateTime
import io.xxx.omni.omc.util.zoneOffset
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.util.StringUtils
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯惠聚
 */
@Suppress("UnstableApiUsage")
abstract class TxHjPorter : Porter() {

    private val httpUrl = "https://qy.sr.qq.com/openapi/v1"

    protected abstract val path: String

    private val limiter = RateLimiter.create(3.0)

    override fun acquire() {
        limiter.acquire()
    }

    override val pageSize = 50

    override val duration: Duration = Duration.ofMinutes(30)

    override fun getStartTime(): LocalDateTime {
        val minStartTime = LocalDateTime.now().minusMonths(3).plusMinutes(1)
        return if (storeJob.endTime!! < minStartTime) minStartTime else storeJob.endTime!!
    }

    override fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? {
        val response = getResponse(store, startTime, endTime)
        return response.getLong("totalCount")
    }

    protected fun getResponse(
        store: Store,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1,
    ): JSONObject {
        val params = buildRequest(startTime, endTime, pageNo)
        val request = TreeMap(params)
        request["requestId"] = UUID.randomUUID().toString().replace("-".toRegex(), "")
        request["appId"] = store.appKey!!
        request["timestamp"] = System.currentTimeMillis().toString()
        request["nonceStr"] = DigestUtils.md5Hex(request["timestamp"]).substring(4, 10)
        val builder = StringBuilder()
            .append(request.entries.joinToString("&"))
            .append("&").append("appSecret").append("=").append(store.appSecret!!)
        val sign = DigestUtils.md5Hex(builder.toString()).uppercase()
        val url = UriComponentsBuilder
            .fromHttpUrl(httpUrl)
            .path(path)
            .toUriString()
        request["sign"] = sign
        val response = restTemplate.postForEntity(url, request, JSONObject::class.java).body!!
        val code = response.getIntValue("code")
        if (code != 0) {
            throwException(url, response.toJSONString())
        }
        return response.getJSONObject("data")
    }

    protected abstract fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long = 1
    ): Map<String, String>
}

// 订单
@Suppress("unused")
class TxHjTradePorter : TxHjPorter() {

    override val path = "/order/list/increment"

    override val documentType = DocumentType.TRADE

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(store, startTime, endTime, pageNo)
        val dataKey = store.accessToken!!
        return response.getJSONArray("orderList")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                value["userName"] = decrypt(value.getString("userName"), dataKey)
                value["telNumber"] = decrypt(value.getString("telNumber"), dataKey)
                value["provinceName"] = decrypt(value.getString("provinceName"), dataKey)
                value["cityName"] = decrypt(value.getString("cityName"), dataKey)
                value["countyName"] = decrypt(value.getString("countyName"), dataKey)
                value["detailInfo"] = decrypt(value.getString("detailInfo"), dataKey)
                value["deliverNumber"] = decrypt(value.getString("deliverNumber"), dataKey)
                value["deliverCompany"] = decrypt(value.getString("deliverCompany"), dataKey)
                val sn = value.getString("orderId")
                Document(sn, value, value.getLong("updateTime").toLocalDateTime())
            }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long
    ): Map<String, String> {
        return mapOf(
            "startUpdateTime" to startTime.toEpochSecond(zoneOffset).toString(),
            "endUpdateTime" to endTime.toEpochSecond(zoneOffset).toString(),
            "pageNum" to pageNo.toString(),
            "pageSize" to pageSize.toString(),
        )
    }

    private fun decrypt(encryptedData: String, dataKey: String): String? {
        return if (!StringUtils.hasText(encryptedData)) {
            null
        } else decrypt(Base64.getDecoder().decode(encryptedData), dataKey)
    }

    private fun decrypt(encryptedIvTextBytes: ByteArray, key: String): String {
        val ivSize = 16
        val keySize = 16
        val iv = ByteArray(ivSize)
        System.arraycopy(encryptedIvTextBytes, 0, iv, 0, iv.size)
        val ivParameterSpec = IvParameterSpec(iv)
        val encryptedSize = encryptedIvTextBytes.size - ivSize
        val encryptedBytes = ByteArray(encryptedSize)
        System.arraycopy(encryptedIvTextBytes, ivSize, encryptedBytes, 0, encryptedSize)
        val keyBytes = ByteArray(keySize)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(key.toByteArray())
        System.arraycopy(md.digest(), 0, keyBytes, 0, keyBytes.size)
        val secretKeySpec = SecretKeySpec(keyBytes, "AES")
        val cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipherDecrypt.init(2, secretKeySpec, ivParameterSpec)
        val decrypted = cipherDecrypt.doFinal(encryptedBytes)
        return String(decrypted)
    }
}

@Suppress("unused")
class TxHjRefundPorter : TxHjPorter() {

    override val path = "/aftersale/list/increment"

    override val documentType = DocumentType.REFUND

    override fun getData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?,
        pageNo: Long,
    ): List<Document> {
        val response = getResponse(store, startTime, endTime, pageNo)
        return response.getJSONArray("list")
            .map {
                @Suppress("unchecked_cast")
                val value = JSONObject(it as Map<String, Any>)
                val sn = value.getString("afterSaleOrderId")
                val rsn = value.getString("orderId")
                Document(sn, rsn, value, value.getLong("updateTime").toLocalDateTime())
            }
    }

    override fun buildRequest(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        pageNo: Long
    ): Map<String, String> {
        return mapOf(
            "startUpdateTime" to startTime.toEpochSecond(zoneOffset).toString(),
            "endUpdateTime" to endTime.toEpochSecond(zoneOffset).toString(),
            "pageNum" to pageNo.toString(),
            "pageSize" to pageSize.toString(),
        )
    }
}