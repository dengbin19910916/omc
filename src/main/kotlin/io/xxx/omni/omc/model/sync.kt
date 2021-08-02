package io.xxx.omni.omc.model

import com.alibaba.fastjson.JSONObject
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import io.xxx.omni.omc.util.gson
import io.xxx.omni.omc.util.jsonb
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.Param
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.`java-time`.datetime
import java.time.LocalDateTime

/**
 * 报文
 */
data class Document(
    @TableId(type = IdType.AUTO)
    var id: Long?,
    var sid: String?,
    var sn: String?,
    var rsn: String?,
    var data: JSONObject?,
    var modified: LocalDateTime?,
    var pollCreated: LocalDateTime?,
    var pollModified: LocalDateTime?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null)

    constructor(sn: String?, data: JSONObject?, modified: LocalDateTime?)
            : this(null, null, sn, null, data, modified, null, null)

    constructor(sn: String?, rsn: String?, data: JSONObject?, modified: LocalDateTime?)
            : this(null, null, sn, rsn, data, modified, null, null)

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return when (other) {
            is Document -> other.sid == sid && other.sn == sn && other.modified == modified
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = sid?.hashCode() ?: 0
        result = 31 * result + (sn?.hashCode() ?: 0)
        result = 31 * result + (modified?.hashCode() ?: 0)
        return result
    }
}

@Mapper
interface DocumentMapper : BaseMapper<Document> {

    /**
     * 根据sid和sn判断是否是否存在，不存在时插入数据，否则更新数据。
     */
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    fun upsertAll(@Param("list") documents: List<Document>): List<Document>
}

object DocumentTable : Table() {
    val id = long("id")
    val sid = varchar("sid", 50)
    val sn = varchar("sn", 50)
    val rsn = varchar("rsn", 50)
    val data = jsonb("data", JSONObject::class.java, gson, true)
    val modified = datetime("modified")
    val pollCreated = datetime("poll_created")
    val pollModified = datetime("poll_modified")
}

enum class DocumentType {
    NONE,
    TRADE,
    REFUND,
    MIXED
}

/**
 * 发送MQ失败等待重试的Document
 */
data class RetriedDocument(
    @TableId(type = IdType.AUTO)
    var id: Long?,
    var did: Long?,
    var retries: Int?,
    var created: LocalDateTime?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null)

    constructor(did: Long?) : this(null, did, 3, LocalDateTime.now())
}

@Mapper
interface RetriedDocumentMapper : BaseMapper<RetriedDocument>

data class KafkaOffset(
    var id: Long?,
    var topic: String?,
    var partition: Int?,
    var offset: Long?
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null)

    constructor(topic: String, partition: Int, offset: Long) : this(null, topic, partition, offset)
}

@Mapper
interface KafkaOffsetMapper : BaseMapper<KafkaOffset>