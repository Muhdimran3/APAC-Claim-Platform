package com.example.claims.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Error handling for the projection consumers. Unparseable messages never get here (the codec
 * skips them). This covers transient failures such as the database being briefly unavailable:
 * retry 5 times, 1 s apart, then log and move on so the partition isn't blocked forever.
 * A dead-letter topic is out of scope (Entry 3, section G).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error("Projection gave up on {}-{}@{} (key {})",
                        record.topic(), record.partition(), record.offset(), record.key(), ex),
                new FixedBackOff(1000L, 5));
    }
}
