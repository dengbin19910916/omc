package io.xxx.omni.omc.util

import org.apache.commons.codec.digest.DigestUtils
import java.util.*

fun sign(appSecret: String, signMap: Map<String, String>): String {
    val map = if (signMap is TreeMap) signMap else TreeMap(signMap)
    val builder = StringBuilder()
        .append(appSecret)
        .append(map.entries.joinToString("") { it.key + it.value })
        .append(appSecret)
    return DigestUtils.md5Hex(builder.toString()).uppercase()
}