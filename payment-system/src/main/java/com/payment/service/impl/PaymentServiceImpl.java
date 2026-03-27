package com.payment.service.impl;

import com.payment.config.StripeConfig;
import com.payment.dto.PaymentEvent;
import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.RefundRequest;
import com.payment.entity.Payment;
import com.payment.entity.PaymentStatus;
import com.payment.exception.PaymentException;
import com.payment.kafka.PaymentKafkaProducer;
import com.payment.repository.PaymentRepository;
import com.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final StripeConfig stripeConfig;
    private final PaymentKafkaProducer kafkaProducer;

    @Override
    @Transactional
    public PaymentResponse createPaymentIntent(PaymentRequest request) {
        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency().toLowerCase())
                .status(PaymentStatus.PENDING)
                .stripeCustomerId(request.getCustomerId())
                .description(request.getDescription())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Saved pending payment id {}, publishing to Kafka", saved.getId());

        kafkaProducer.publishPaymentRequest(PaymentEvent.builder()
                .paymentId(saved.getId())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .description(saved.getDescription())
                .customerId(saved.getStripeCustomerId())
                .build());

        return toResponse(saved, null);
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(Long paymentId) {
        Payment payment = findPaymentById(paymentId);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toResponse(payment, null);
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentException(
                    "Cannot confirm a refunded payment",
                    HttpStatus.CONFLICT,
                    "PAYMENT_ALREADY_REFUNDED"
            );
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentException(
                    "Cannot confirm a failed payment",
                    HttpStatus.CONFLICT,
                    "PAYMENT_ALREADY_FAILED"
            );
        }

        PaymentIntent paymentIntent;
        try {
            paymentIntent = PaymentIntent.retrieve(payment.getStripePaymentIntentId());
        } catch (StripeException e) {
            log.error("Failed to retrieve PaymentIntent {}: {}", payment.getStripePaymentIntentId(), e.getMessage(), e);
            throw new PaymentException(
                    "Failed to retrieve payment intent from Stripe: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "STRIPE_ERROR",
                    e
            );
        }

        String stripeStatus = paymentIntent.getStatus();
        PaymentStatus newStatus = mapStripeStatus(stripeStatus);
        payment.setStatus(newStatus);
        Payment updated = paymentRepository.save(payment);

        log.info("Confirmed payment id {} with status {}", paymentId, newStatus);
        return toResponse(updated, null);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(Long paymentId, RefundRequest refundRequest) {
        Payment payment = findPaymentById(paymentId);

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentException(
                    "Only succeeded payments can be refunded. Current status: " + payment.getStatus(),
                    HttpStatus.CONFLICT,
                    "PAYMENT_NOT_REFUNDABLE"
            );
        }

        RefundCreateParams.Builder refundParamsBuilder = RefundCreateParams.builder()
                .setPaymentIntent(payment.getStripePaymentIntentId());

        if (refundRequest != null && refundRequest.getAmount() != null) {
            refundParamsBuilder.setAmount(refundRequest.getAmount());
        }

        if (refundRequest != null && refundRequest.getReason() != null) {
            RefundCreateParams.Reason reason = parseRefundReason(refundRequest.getReason());
            if (reason != null) {
                refundParamsBuilder.setReason(reason);
            }
        }

        try {
            Refund.create(refundParamsBuilder.build());
        } catch (StripeException e) {
            log.error("Failed to create Stripe refund for payment {}: {}", paymentId, e.getMessage(), e);
            throw new PaymentException(
                    "Failed to process refund: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY,
                    "STRIPE_REFUND_ERROR",
                    e
            );
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment updated = paymentRepository.save(payment);

        log.info("Refunded payment id {}", paymentId);
        return toResponse(updated, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = findPaymentById(paymentId);
        return toResponse(payment, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments() {
        return paymentRepository.findAll().stream()
                .map(p -> toResponse(p, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void handleStripeEvent(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new PaymentException(
                    "Invalid webhook signature",
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WEBHOOK_SIGNATURE",
                    e
            );
        }

        log.info("Received Stripe webhook event: {} ({})", event.getType(), event.getId());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "charge.refunded" -> handleChargeRefunded(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(stripeObject -> {
            if (stripeObject instanceof PaymentIntent paymentIntent) {
                updatePaymentStatusByIntentId(paymentIntent.getId(), PaymentStatus.SUCCEEDED);
            }
        });
    }

    private void handlePaymentIntentFailed(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(stripeObject -> {
            if (stripeObject instanceof PaymentIntent paymentIntent) {
                updatePaymentStatusByIntentId(paymentIntent.getId(), PaymentStatus.FAILED);
            }
        });
    }

    private void handleChargeRefunded(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresent(stripeObject -> {
            if (stripeObject instanceof com.stripe.model.Charge charge) {
                String paymentIntentId = charge.getPaymentIntent();
                if (paymentIntentId != null) {
                    updatePaymentStatusByIntentId(paymentIntentId, PaymentStatus.REFUNDED);
                }
            }
        });
    }

    private void updatePaymentStatusByIntentId(String paymentIntentId, PaymentStatus newStatus) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId).ifPresentOrElse(
                payment -> {
                    payment.setStatus(newStatus);
                    paymentRepository.save(payment);
                    log.info("Updated payment {} status to {} via webhook", payment.getId(), newStatus);
                },
                () -> log.warn("Received webhook for unknown PaymentIntent: {}", paymentIntentId)
        );
    }

    private Payment findPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(
                        "Payment not found with id: " + paymentId,
                        HttpStatus.NOT_FOUND,
                        "PAYMENT_NOT_FOUND"
                ));
    }

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> PaymentStatus.SUCCEEDED;
            case "canceled" -> PaymentStatus.FAILED;
            default -> PaymentStatus.PENDING;
        };
    }

    private RefundCreateParams.Reason parseRefundReason(String reason) {
        return switch (reason.toLowerCase()) {
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            case "requested_by_customer" -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
            default -> null;
        };
    }

    private PaymentResponse toResponse(Payment payment, String clientSecret) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .stripeCustomerId(payment.getStripeCustomerId())
                .description(payment.getDescription())
                .clientSecret(clientSecret != null ? clientSecret : payment.getClientSecret())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
