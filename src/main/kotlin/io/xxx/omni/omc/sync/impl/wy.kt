package io.xxx.omni.omc.sync.impl

import io.xxx.omni.omc.sync.Porter
import java.util.*

/**
 * 微盟
 *
 * https://cloud.weimob.com/saas/word/detail.html?tag=1046&menuId=2
 */
abstract class WmPorter : Porter() {

    protected fun getResponse() {
        val request = buildRequest()
        val signMap = TreeMap(request)
    }

    protected abstract fun buildRequest(): Map<String, Any>
}

// 订单
class WmTradePorter : WmPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}


// 订单
class WmRefundPorter : WmPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}