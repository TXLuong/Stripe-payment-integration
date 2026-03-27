package com.payment.controller;

import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.RefundRequest;
import com.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPaymentIntent(@Valid @RequestBody PaymentRequest request) {
        log.info("Enqueuing payment request for amount {} {}", request.getAmount(), request.getCurrency());
        PaymentResponse response = paymentService.createPaymentIntent(request);
        // 202 Accepted: payment is saved as PENDING and queued to Kafka for async Stripe processing.
        // Poll GET /api/payments/{id} to retrieve clientSecret once the consumer processes it.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        log.info("Retrieving payment with id {}", id);
        PaymentResponse response = paymentService.getPayment(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> listPayments() {
        log.info("Listing all payments");
        List<PaymentResponse> responses = paymentService.listPayments();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(@PathVariable Long id) {
        log.info("Confirming payment with id {}", id);
        PaymentResponse response = paymentService.confirmPayment(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundRequest refundRequest) {
        log.info("Refunding payment with id {}", id);
        PaymentResponse response = paymentService.refundPayment(id, refundRequest);
        return ResponseEntity.ok(response);
    }
}
