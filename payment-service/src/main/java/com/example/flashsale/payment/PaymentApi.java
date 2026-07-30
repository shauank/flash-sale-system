package com.example.flashsale.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;

@RestController
public class PaymentApi {
	public record Request(@NotBlank String orderId, @NotBlank String userId,
			@NotNull @DecimalMin("0.01") BigDecimal amount) {
	}

	public record Response(String paymentId, String orderId, String userId, BigDecimal amount, Payment.Status status,
			String failureReason, Instant createdAt, Instant updatedAt) {
		static Response from(Payment p) {
			return new Response(p.getPaymentId(), p.getOrderId(), p.getUserId(), p.getAmount(), p.getStatus(),
					p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
		}
	}

	private final PaymentService service;

	PaymentApi(PaymentService service) {
		this.service = service;
	}

	@PostMapping("/internal/payments")
	@ResponseStatus(HttpStatus.CREATED)
	Response process(@Valid @RequestBody Request request) {
		MDC.put("orderId", request.orderId());
		MDC.put("userId", request.userId());
		Payment p = service.process(request);
		MDC.put("paymentId", p.getPaymentId());
		return Response.from(p);
	}
}
