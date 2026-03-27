package com.payment.dto;

import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @Min(value = 1, message = "Refund amount must be at least 1 cent")
    private Long amount;

    private String reason;
}
