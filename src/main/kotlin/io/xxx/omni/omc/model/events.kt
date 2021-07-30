package io.xxx.omni.omc.model

import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

/**
 * 当任务不在需要时销毁任务
 */
class JobDestroyEvent(source: StoreJob) : ApplicationEvent(source)

@Component
class JobDestroyEventHandler : ApplicationListener<JobDestroyEvent> {

    override fun onApplicationEvent(event: JobDestroyEvent) {
        println(event)
    }
}