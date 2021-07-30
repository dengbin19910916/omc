package io.xxx.omni.omc.client

import io.xxx.omni.omc.model.Platform
import io.xxx.omni.omc.model.Store
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@CacheConfig(cacheNames = ["platforms"])
@Service
class PlatformService {

    @Autowired
    private lateinit var client: PlatformClient

    @Cacheable
    fun getAll(@RequestParam("enabled") enabled: Boolean = true): List<Platform> {
        return client.getAll(enabled)
    }

    @Cacheable
    fun getOne(sid: String): Platform? {
        return client.getOne(sid)
    }
}

@CacheConfig(cacheNames = ["stores"])
@Service
class StoreService {

    @Autowired
    private lateinit var client: StoreClient

    @Cacheable
    fun getAll(pid: String? = null, enabled: Boolean = true): List<Store> {
        return client.getAll(pid, enabled)
    }

    @Cacheable
    fun getOne(sid: String): Store? {
        return client.getOne(sid)
    }
}

@FeignClient("mdc")
@RequestMapping("/platforms")
interface PlatformClient {

    @GetMapping
    fun getAll(@RequestParam("enabled") enabled: Boolean): List<Platform>

    @GetMapping("/{id}")
    fun getOne(@PathVariable("id") id: String): Platform?
}

@FeignClient("mdc")
@RequestMapping("/stores")
interface StoreClient {

    @GetMapping
    fun getAll(@RequestParam("pid") pid: String?, @RequestParam("enabled") enabled: Boolean): List<Store>

    /**
     * 返回的门店信息默认包含平台信息。
     * @param fully 是否包含平台信息，true - 包含，false - 不包含
     */
    @GetMapping("/{id}")
    fun getOne(@PathVariable("id") id: String, @RequestParam("fully") fully: Boolean = true): Store?
}