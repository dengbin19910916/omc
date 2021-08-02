package io.xxx.omni.omc.sync

import com.alibaba.fastjson.JSON
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import io.xxx.omni.omc.client.PlatformService
import io.xxx.omni.omc.client.StoreService
import io.xxx.omni.omc.config.OmniProperties
import io.xxx.omni.omc.model.*
import io.xxx.omni.omc.util.dateTimeFormatter
import io.xxx.omni.omc.util.numberFormat
import io.xxx.omni.omc.util.pool
import okhttp3.OkHttpClient
import org.quartz.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.Future

/**
 * 通过门店信息初始化任务。
 * 一个门店对应一个任务，如果门店被禁用/启用则销毁/加载任务。
 */
@Component
class Synchronizer : ApplicationRunner {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var platformService: PlatformService

    @Autowired
    private lateinit var storeService: StoreService

    @Autowired
    private lateinit var jobMapper: JobMapper

    @Autowired
    private lateinit var platformJobMapper: PlatformJobMapper

    @Autowired
    private lateinit var storeJobMapper: StoreJobMapper

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var scheduler: Scheduler

    /**
     * 读取DB数据更新任务
     */
    @Scheduled(cron = "0/1 * * * * ?")
    fun flush() {
//        val platformJobs = platformJobMapper.selectList(null)
//        for (platformJob in platformJobs) {
//            val simpleName = platformJob.jobClass!!.substringAfterLast(".")
//            val beanName = simpleName.replaceFirstChar { it.lowercase() } + "Porter"
//            val jobKey = JobKey(beanName, "SYNC")
//            scheduler.interrupt(jobKey)
//        }
    }

    override fun run(args: ApplicationArguments) {
        fun getJob(storeJob: StoreJob): Triple<String, JobDetail, Trigger> {
            val beanClass = Class.forName(storeJob.platformJob!!.jobClass!!)
            val builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass)
                .addPropertyValue("storeJob", storeJob)
            val beanDefinition = builder.beanDefinition
            val beanName = "${beanClass.simpleName.replaceFirstChar { it.lowercase() }}${storeJob.sid}Porter"
            (applicationContext as GenericApplicationContext).registerBeanDefinition(beanName, beanDefinition)
            val porter = applicationContext.getBean(beanName, Porter::class.java)
            val jobDetail = JobBuilder.newJob(ProxyJob::class.java)
                .withIdentity("${beanName}Job", "SYNC")
                .usingJobData(JobDataMap(mapOf("store" to storeJob.store, "porter" to porter)))
                .build()
            val trigger = TriggerBuilder.newTrigger()
                .withIdentity("${beanName}Trigger", "SYNC")
                .withSchedule(CronScheduleBuilder.cronSchedule(porter.cron))
                .build()
            return Triple(beanName, jobDetail, trigger)
        }

        // 根据已启用的店铺信息创建定时任务
        val platforms = platformService.getAll()
        for (platform in platforms) {
            if (!platform.enabled) continue
            val stores = storeService.getAll(platform.id)
            for (store in stores) {
                if (!store.enabled) continue
                val pjWrapper = KtQueryWrapper(PlatformJob::class.java)
                    .eq(PlatformJob::pid, platform.id)
                val platformJobs = platformJobMapper.selectList(pjWrapper)
                for (platformJob in platformJobs) {
                    if (!platformJob.enabled!!) continue
                    val job = jobMapper.selectById(platformJob.jid) ?: continue
                    if (!job.enabled!!) continue
                    val sjWrapper = KtQueryWrapper(StoreJob::class.java)
                        .eq(StoreJob::sid, store.id)
                        .eq(StoreJob::jid, platformJob.jid)
                    val storeJob = storeJobMapper.selectOne(sjWrapper) ?: continue
                    if (!storeJob.enabled!!) continue
                    storeJob.job = job
                    storeJob.store = store
                    storeJob.platformJob = platformJob
                    store.platform = platform
                    val (beanName, jobDetail, trigger) = getJob(storeJob)
                    scheduler.scheduleJob(jobDetail, trigger)
                    log.info("${beanName}任务已注册")
                }
            }
        }
    }
}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class ProxyJob : QuartzJobBean(), InterruptableJob {

    private lateinit var porter: Porter

    override fun executeInternal(context: JobExecutionContext) {
        val dataMap = context.mergedJobDataMap
        porter = (dataMap["porter"] as Porter)
        porter.run()
    }

    override fun interrupt() {
        porter.interrupt()
    }
}


/**
 * 数据同步拆分为2部分：
 * 1. 获取时间段内的数据总数
 * 2. 分页获取时间段内的数据
 * 平台响应的数据状态如果为失败，则抛出RuntimeException。
 */
@Suppress("SpringJavaAutowiredMembersInspection")
abstract class Porter {

    protected val log: Logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    protected lateinit var omniProperties: OmniProperties

    @Autowired
    protected lateinit var jobMapper: JobMapper

    @Autowired
    protected lateinit var documentMapper: DocumentMapper

    @Autowired
    protected lateinit var retriedDocumentMapper: RetriedDocumentMapper

    @Autowired
    protected lateinit var kafkaOffsetMapper: KafkaOffsetMapper

    @Autowired
    protected lateinit var platformJobMapper: PlatformJobMapper

    @Autowired
    protected lateinit var storeJobMapper: StoreJobMapper

    @Autowired
    protected lateinit var platformService: PlatformService

    @Autowired
    protected lateinit var storeService: StoreService

    @Autowired
    protected lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    protected lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    protected lateinit var producerFactory: ProducerFactory<String, String>

    @Autowired
    protected lateinit var lbRestTemplate: RestTemplate

    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    protected val restTemplate: RestTemplate by lazy {
        restTemplateBuilder
            .requestFactory {
                OkHttp3ClientHttpRequestFactory(client)
            }
            .build()
    }

    protected val client = OkHttpClient.Builder()
        .readTimeout(Duration.ofSeconds(120))
        .build()

    lateinit var storeJob: StoreJob // 手工注入

    protected val store by lazy { storeJob.store!! }

    @Volatile
    private var interruptted: Boolean = false

    /**
     * 中断任务
     */
    fun interrupt() {
        this.interruptted = true
    }

    /**
     * 计划任务
     */
    open val cron = "0/1 * * * * ?"

    /**
     * 限制开始时间和结束时间的时间范围不超过平台的限制
     */
    protected open val duration: Duration = Duration.ZERO

    /**
     * 数据延迟时长
     */
    protected open val delay: Duration = Duration.ZERO

    /**
     * 申请可继续操作的资源，ex：进行限流等操作
     */
    protected open fun acquire() {}

    /**
     * 使用不同的参数多次调用接口
     * @return 第一个参数为扩展参数，第二个参数为并行标志
     */
    protected open fun getParameters(): Pair<List<Any?>, Boolean/*是否可以并行处理*/> = listOf(null) to false

    /**
     * 返回平台限制的开始时间，部分平台只允许查询3个月以内的订单
     */
    protected open fun getStartTime(): LocalDateTime = storeJob.endTime!!

    /**
     * 分页起始页
     */
    protected open val startPage = 1

    /**
     * 每页大小
     */
    protected open val pageSize = 100

    /**
     * 报文类型
     */
    protected open val documentType = DocumentType.NONE

    fun run() {
        /**
         * 数据同步需要一对开始时间和结束时间，根据平台对于开始时间和结束时间的时间范围约束生成
         */
        fun getTimeRanges(startTime: LocalDateTime, endTime: LocalDateTime): Set<Pair<LocalDateTime, LocalDateTime>> {
            val timeRanges = linkedSetOf<Pair<LocalDateTime, LocalDateTime>>()
            if (duration == Duration.ZERO) {
                timeRanges.add(startTime to endTime)
            } else {
                val totalSeconds = Duration.between(startTime, endTime).abs().seconds
                val indexes = totalSeconds / duration.seconds + if (totalSeconds % duration.seconds == 0L) 0 else 1
                for (i in 1..indexes) {
                    val start = startTime.plusSeconds(duration.seconds * (i - 1))
                    val end = if (i == indexes) endTime else startTime.plusSeconds(duration.seconds * i)
                    timeRanges.add(start to end)
                }
            }
            return timeRanges
        }

        val start = System.currentTimeMillis()

        // TODO 支持interrupt
        if (storeJob.endTime == null) {
            throw RuntimeException("任务启始时间不能为空")
        }

        val startTime = getStartTime()
        val endTime = LocalDateTime.now().minusMinutes(delay.toMinutes())
        if (startTime >= endTime) return
        val timeRanges = getTimeRanges(startTime, endTime)

        val (parameters, parallel) = getParameters()
        for (timeRange in timeRanges) {
            val stream = parameters.stream()
            if (parallel) stream.parallel()
            stream.forEach {
                acquire()
                pullAndSave(timeRange.first, timeRange.second, it)
            }
            storeJob.endTime = timeRange.second
            storeJobMapper.updateById(storeJob)
        }
        val spend = numberFormat.format((System.currentTimeMillis() - start) / 1000.0)
        val st = startTime.format(dateTimeFormatter)
        val et = endTime.format(dateTimeFormatter)
        log.info("${getLogPrefix()} [$st - $et] 数据同步完成，耗时 $spend 秒")
    }

    /**
     * 拉取并保存数据
     * @return 单号列表
     */
    protected open fun pullAndSave(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        parameter: Any?
    ) {
        if (interruptted) {
            throw RuntimeException("任务中断")
        }

        val count = getCount(startTime, endTime, parameter) ?: return
        val pages = count / pageSize + if (count % pageSize > 0) 1 else 0

        val numberFormat = DecimalFormat("#,###")
        val stf = startTime.format(dateTimeFormatter)
        val etf = endTime.format(dateTimeFormatter)
        log.info("${getLogPrefix()} [$stf - $etf]: ${numberFormat.format(count)} 条数据")

        val futures = mutableListOf<Future<*>>()
        var pageNo = pages
        while (pageNo-- > 0) {
            if (interruptted) {
                throw RuntimeException("任务中断")
            }

            val data = getData(startTime, endTime, parameter, pageNo + startPage)
            val pf = numberFormat.format(pageNo + if (startPage == 0) 1 else startPage)
            val psf = numberFormat.format(pages)

            val future = pool.submit {
                val saved = save(store, data)
                sendMessages(saved)
            }
            futures.add(future)
            log.info("${getLogPrefix()} [$stf - $etf]: $pf / $psf 同步完成")
        }
        for (future in futures) {
            future.get()
        }
    }

    private fun getLogPrefix(): String {
        return "${store.platform!!.name}(${store.pid}) - ${store.name}(${store.id})"
    }

    /**
     * 返回时间范围内的数据总数。
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param parameter 扩展参数
     * @return 数据总数
     */
    protected open fun getCount(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?): Long? =
        throw NotImplementedError()


    /**
     * 返回时间范围内的文档列表。
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param parameter 扩展参数
     * @param pageNo 第几页
     * @return 文档列表
     */
    open fun getData(startTime: LocalDateTime, endTime: LocalDateTime, parameter: Any?, pageNo: Long): List<Document>? =
        throw NotImplementedError()

    open fun sendMessages(documents: List<Document>) {
        val store = storeJob.store!!
        val platform = storeJob.store!!.platform!!
        val topic = (platform.id + documentType.name).uppercase()
        for (document in documents) {
            val data = mutableMapOf<String, Any>(
                "pid" to platform.id,
                "sid" to store.id,
                "sn" to document.sn!!,
                "modified" to document.modified!!
            )
            val future = kafkaTemplate.send(topic, JSON.toJSONString(data))
            future.addCallback({
                val rm = it!!.recordMetadata
                val kafkaOffset = KafkaOffset(rm.topic(), rm.partition(), rm.offset())
                kafkaOffsetMapper.insert(kafkaOffset)
            }, {
                val documentRetry = RetriedDocument(document.id)
                retriedDocumentMapper.insert(documentRetry)
            })
        }
    }

    /**
     * 保存不存在的文档，更新已存在的文档（更新时间大于已存在的文档）
     * @return 新增或更新的文档列表（不包含已经存在但未更新的文档）
     */
    protected open fun save(store: Store, data: List<Document>?): List<Document> {
        if (data.isNullOrEmpty())
            return emptyList()
        data.forEach { if (it.sid == null) it.sid = store.id }
        return documentMapper.upsertAll(data)
    }

    /**
     * 记录错误日志并抛出RuntimeException。
     * @param api url path 或 method
     * @param details 错误详情
     */
    fun throwException(api: String?, details: String) {
        val store = storeJob.store
        val job = storeJob.job
        val message = "${store?.name}(${store?.id})${job?.name} - $api 调用失败, 原因: $details"
        log.error(message)
        throw RuntimeException(message)
    }
}