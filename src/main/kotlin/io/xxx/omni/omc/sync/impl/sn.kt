package io.xxx.omni.omc.sync.impl

import io.xxx.omni.omc.sync.Porter
import java.util.*

/**
 * 苏宁
 *
 * https://open.suning.com/ospos/apipage/toDocContent.do?menuId=23
 *
 * https://open.suning.com/ospos/apipage/toDocContent.do?menuId=53
 */
abstract class SnPorter : Porter() {

    private val httpUrl = "https://open.suning.com/api/http/sopRequest"

    protected fun getResponse() {
        val request = buildRequest()
        val signMap = TreeMap(request)
    }

    protected abstract fun buildRequest(): Map<String, Any>
}

// 订单
class SnTradePorter : SnPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}


// 订单
class SnRefundPorter : SnPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}