package com.payment.webhook;

import com.payment.exception.PaymentException;
import com.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final PaymentService paymentService;

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(STRIPE_SIGNATURE_HEADER) String sigHeader) {

        log.info("Received Stripe webhook event");

        try {
            paymentService.handleStripeEvent(payload, sigHeader);
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (PaymentException ex) {
            if ("INVALID_WEBHOOK_SIGNATURE".equals(ex.getErrorCode())) {
                log.warn("Webhook signature verification failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid signature"));
            }
            log.error("Error processing webhook: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed"));
        }
    }
}
