package com.payment.consumer.service;

import com.payment.consumer.dto.PaymentEvent;
import com.payment.consumer.entity.Payment;
import com.payment.consumer.entity.PaymentStatus;
import com.payment.consumer.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public void process(PaymentEvent event) {
        Payment payment = paymentRepository.findById(event.getPaymentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found: " + event.getPaymentId()));

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(event.getAmount())
                .setCurrency(event.getCurrency().toLowerCase())
                .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            params.setDescription(event.getDescription());
        }

        if (event.getCustomerId() != null && !event.getCustomerId().isBlank()) {
            params.setCustomer(event.getCustomerId());
        }

        try {
            PaymentIntent intent = PaymentIntent.create(params.build());
            payment.setStripePaymentIntentId(intent.getId());
            payment.setClientSecret(intent.getClientSecret());
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
            log.info("Processed paymentId={} → Stripe intentId={}", event.getPaymentId(), intent.getId());
        } catch (StripeException e) {
            log.error("Stripe error for paymentId={}: {}", event.getPaymentId(), e.getMessage(), e);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }
}
