package com.payment.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Amount is required")
    @Min(value = 50, message = "Amount must be at least 50 cents")
    private Long amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[a-zA-Z]{3}$", message = "Currency must contain only letters")
    private String currency;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    private String customerId;
}
