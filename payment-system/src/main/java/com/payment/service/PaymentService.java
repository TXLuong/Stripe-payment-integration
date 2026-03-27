package com.payment.service;

import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.RefundRequest;

import java.util.List;

public interface PaymentService {

    /**
     * Saves a PENDING payment record and publishes a PaymentEvent to Kafka.
     * Returns immediately without calling Stripe — processing is async via payment-consumer.
     */
    PaymentResponse createPaymentIntent(PaymentRequest request);

    PaymentResponse confirmPayment(Long paymentId);

    PaymentResponse refundPayment(Long paymentId, RefundRequest refundRequest);

    PaymentResponse getPayment(Long paymentId);

    List<PaymentResponse> listPayments();

    void handleStripeEvent(String payload, String sigHeader);
}
