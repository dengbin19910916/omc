package io.xxx.omni.omc.util

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.google.gson.GsonBuilder
import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedTypes
import org.postgresql.util.PGobject
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

val gson = GsonBuilder()
    .create()

private val processors = Runtime.getRuntime().availableProcessors()

val pool = ThreadPoolExecutor(
    processors, 4 * processors,
    120, TimeUnit.SECONDS,
    ArrayBlockingQueue(32),
    ThreadPoolExecutor.CallerRunsPolicy()
)

val numberFormat: NumberFormat = DecimalFormat("#,##0.00")

val zoneOffset: ZoneOffset = OffsetDateTime.now().offset

const val dateTimeFormatPattern = "yyyy-MM-dd HH:mm:ss"

val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormatPattern)

fun String?.toLocalDateTime(): LocalDateTime? {
    if (this == null) {
        return null
    }
    return LocalDateTime.parse(this, dateTimeFormatter)
}

fun Long?.toLocalDateTime(): LocalDateTime? {
    if (this == null) {
        return null
    }
    return LocalDateTime.ofEpochSecond(this / 1000, (this % 1000).toInt(), zoneOffset)
}

fun LocalDateTime.toDate(): Date {
    return Date(this.toInstant(zoneOffset).toEpochMilli())
}

fun Date.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(this.toInstant(), zoneOffset)
}

fun Any.toJSONString(): String {
    return JSON.toJSONString(this)
}

fun Any.toJSONObject(): JSONObject {
    return JSON.parseObject(JSON.toJSONString(this))
}

@MappedTypes(Any::class)
class JsonbTypeHandler : BaseTypeHandler<Any>() {

    private val jsonObject = PGobject()

    override fun setNonNullParameter(ps: PreparedStatement, i: Int, parameter: Any, jdbcType: JdbcType?) {
        jsonObject.type = "jsonb"
        jsonObject.value = parameter.toString()
        ps.setObject(i, jsonObject)
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): Any {
        return rs.getObject(columnName);
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): Any {
        return rs.getObject(columnIndex);
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): Any {
        return cs.getObject(columnIndex)
    }

}