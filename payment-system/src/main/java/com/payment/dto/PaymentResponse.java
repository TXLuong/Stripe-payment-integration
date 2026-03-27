package com.payment.dto;

import com.payment.entity.PaymentStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long amount;
    private String currency;
    private PaymentStatus status;
    private String stripePaymentIntentId;
    private String stripeCustomerId;
    private String description;
    private String clientSecret;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
