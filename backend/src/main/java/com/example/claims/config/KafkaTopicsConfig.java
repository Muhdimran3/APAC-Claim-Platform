package com.example.claims.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are declared by the app (broker auto-create is disabled) so the topology is
 * versioned with the code. One lifecycle topic keyed by claimId: all events for a claim
 * land on the same partition and are consumed in order.
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic claimEventsTopic(@Value("${app.kafka.topics.claim-events}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
