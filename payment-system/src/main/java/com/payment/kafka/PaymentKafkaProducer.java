package com.payment.kafka;

import com.payment.dto.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public void publishPaymentRequest(PaymentEvent event) {
        log.info("Publishing payment request to Kafka for paymentId: {}", event.getPaymentId());
        kafkaTemplate.send(
                KafkaTopicConfig.PAYMENT_REQUESTS_TOPIC,
                String.valueOf(event.getPaymentId()),
                event
        );
    }
}
