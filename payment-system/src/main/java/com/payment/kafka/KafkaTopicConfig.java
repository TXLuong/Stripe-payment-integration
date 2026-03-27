package com.payment.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PAYMENT_REQUESTS_TOPIC = "payment.requests";
    public static final String PAYMENT_RESULTS_TOPIC = "payment.results";

    @Bean
    public NewTopic paymentRequestsTopic() {
        return TopicBuilder.name(PAYMENT_REQUESTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentResultsTopic() {
        return TopicBuilder.name(PAYMENT_RESULTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
