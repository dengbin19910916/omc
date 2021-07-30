package io.xxx.omni.omc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConstructorBinding
data class Platform(
    val code: String?,
    val httpUrl: String?,
)

@ConstructorBinding
@ConfigurationProperties(prefix = "omni")
data class OmniProperties(
    val platforms: List<Platform> = emptyList()
) {
    fun getPlatform(code: String): Platform? {
        return platforms.firstOrNull { it.code == code }
    }
}

@Configuration
@EnableConfigurationProperties(OmniProperties::class)
class OmniConfiguration