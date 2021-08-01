package io.xxx.omni.omc.config

import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.apache.http.ssl.TrustStrategy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class WebConfig {

    @Autowired
    private lateinit var restTemplateBuilder: RestTemplateBuilder

    @Bean
    @LoadBalanced
    fun lbRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .setConnectTimeout(Duration.ofMinutes(1))
            .setReadTimeout(Duration.ofMinutes(10))
            .requestFactory(OkHttp3ClientHttpRequestFactory::class.java)
            .build()
    }

    @Bean
    fun sslRestTemplate(): RestTemplate {
        val acceptingTrustStrategy = TrustStrategy { _, _ -> true }
        val sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build()
        val connectionSocketFactory = SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier())
        val httpClient = HttpClients.custom()
            .setSSLSocketFactory(connectionSocketFactory)
            .build()
        val factory = HttpComponentsClientHttpRequestFactory()
        factory.httpClient = httpClient
        factory.setConnectTimeout(60000)
        factory.setReadTimeout(600000)
        return RestTemplate(factory)
    }
}