package io.xxx.omni.omc.model

import com.alibaba.fastjson.JSONObject
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import io.xxx.omni.omc.util.JSONTypeHandler
import io.xxx.omni.omc.util.toLocalDateTime
import org.apache.ibatis.annotations.Mapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 任务信息
 */
data class Job(
    @TableId(type = IdType.AUTO)
    var id: Int?,
    var name: String?,
    var enabled: Boolean?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null)
}

@Mapper
interface JobMapper : BaseMapper<Job>

enum class JobType {
    SYNC,
    TRANSLATE,
    DOCUMENT_HOUSE_KEEPER
}

/**
 * 平台任务关系
 */
data class PlatformJob(
    @TableId(type = IdType.AUTO)
    var id: Int?,
    var pid: String?,
    var jid: Int?,
    var jobClass: String?,
    var enabled: Boolean?,
    @TableField(typeHandler = JSONTypeHandler::class)
    var props: JSONObject?,
    @TableField(exist = false)
    var platform: Platform?,
    @TableField(exist = false)
    var job: Job?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null)
}

@Mapper
interface PlatformJobMapper : BaseMapper<PlatformJob>

/**
 * 店铺任务关系，
 * 根据店铺数据自动生成，如果店铺信息发生变化时将自动更新
 * @see PlatformJob
 */
data class StoreJob(
    @TableId(type = IdType.AUTO)
    var id: Int?,
    var sid: String?,
    var jid: Int?,
    /**
     * 任务最后一次执行的时间，在任务配置时指定
     */
    var endTime: LocalDateTime?,
    var enabled: Boolean?,
    @TableField(typeHandler = JSONTypeHandler::class)
    var props: String?,
    @TableField(exist = false)
    var store: Store?,
    @TableField(exist = false)
    var job: Job?,
    @TableField(exist = false)
    var platformJob: PlatformJob?,
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null, null, null)
}

@Mapper
interface StoreJobMapper : BaseMapper<StoreJob>

/**
 * 爱库存活动信息
 */
data class AkcActivity(
    @TableId
    var id: String?,
    var name: String?,
    var sid: String?,
    var beginTime: LocalDateTime?,
    var endTime: LocalDateTime?,
    var completed: Boolean?,
    @TableField(typeHandler = JSONTypeHandler::class)
    var data: JSONObject?
) {
    @Suppress("unused")
    constructor() : this(null, null, null, null, null, null, null)

    constructor(store: Store, jsonObject: JSONObject) : this(
        jsonObject.getString("id"),
        jsonObject.getString("name"),
        store.id,
        jsonObject.getString("begintime").toLocalDateTime(),
        jsonObject.getString("endtime").toLocalDateTime(),
        false,
        jsonObject
    )

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        return when (other) {
            is AkcActivity -> other.id == id && other.beginTime == beginTime && other.endTime == endTime
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (beginTime?.hashCode() ?: 0)
        result = 31 * result + (endTime?.hashCode() ?: 0)
        return result
    }
}

@Mapper
@Repository
interface AkcActivityMapper : BaseMapper<AkcActivity>