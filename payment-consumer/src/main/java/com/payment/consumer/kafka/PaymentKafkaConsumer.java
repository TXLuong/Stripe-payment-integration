package com.payment.consumer.kafka;

import com.payment.consumer.dto.PaymentEvent;
import com.payment.consumer.service.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final PaymentProcessingService processingService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_REQUESTS,
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${spring.kafka.listener.concurrency:3}"
    )
    public void consume(PaymentEvent event) {
        log.info("Consumed payment event for paymentId={}", event.getPaymentId());
        processingService.process(event);
    }
}
