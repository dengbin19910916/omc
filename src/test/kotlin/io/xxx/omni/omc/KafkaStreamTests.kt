package io.xxx.omni.omc

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.junit.jupiter.api.Test
import java.util.*

class KafkaStreamTests {

    @Test
    fun stream() {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "omc"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] =
            "192.168.206.91:9092,192.168.206.92:9092,192.168.206.93:9092"
        properties[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes::class.java
        properties[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes::class.java
        properties[StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG] = 0
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        StreamsBuilder()
            .stream<String, String>("TAOBAO_TRADE")
//            .
    }
}