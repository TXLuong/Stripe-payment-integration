package com.payment.repository;

import com.payment.entity.Payment;
import com.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByStripeCustomerId(String stripeCustomerId);
}
