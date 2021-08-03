package io.xxx.omni.omc

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*

class KafkaTests {

    @Test
    fun consume() {
        val properties = Properties()
        properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] =
            "192.168.206.91:9092,192.168.206.92:9092,192.168.206.93:9092"
        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        properties[ConsumerConfig.GROUP_ID_CONFIG] = "omc"

        val consumer = KafkaConsumer<String, String>(properties)
        consumer.subscribe(listOf("TAOBAO_TRADE"))

        while (true) {
            val records = consumer.poll(Duration.ofSeconds(5))
            for (record in records) {
                val value = record.value()
                System.err.println(record.topic() + " : " + value)
            }
        }
    }
}