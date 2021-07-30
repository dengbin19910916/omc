package io.xxx.omni.omc.sync.impl

import io.xxx.omni.omc.sync.Porter
import java.util.*

/**
 * 考拉
 *
 * https://open.kaola.com/open/document/9003/9006
 */
abstract class KlPorter : Porter() {

    protected fun getResponse() {
        val request = buildRequest()
        val signMap = TreeMap(request)
    }

    protected abstract fun buildRequest(): Map<String, Any>
}

// 订单
class KlTradePorter : KlPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}


// 订单
class KlRefundPorter : KlPorter() {

    override fun buildRequest(): Map<String, Any> {
        TODO("Not yet implemented")
    }
}