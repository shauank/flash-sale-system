package com.example.flashsale.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;

@RestController
public class OrderApi {
	public record CreateRequest(@NotBlank String requestId, @NotBlank String userId, @NotNull Long productId,
			@Min(1) int quantity) {
	}

	public record Response(String requestId, String orderId, String reservationId, String paymentId,
			Order.Status orderStatus, String reservationStatus, String paymentStatus, String message,
			String failureReason, BigDecimal totalAmount, Instant createdAt) {
	}

	private final OrderWorkflow workflow;

	OrderApi(OrderWorkflow workflow) {
		this.workflow = workflow;
	}

	@PostMapping("/api/orders")
	@ResponseStatus(HttpStatus.CREATED)
	Response create(@Valid @RequestBody CreateRequest request) {
		MDC.put("requestId", request.requestId());
		MDC.put("userId", request.userId());
		MDC.put("productId", String.valueOf(request.productId()));
		return workflow.create(request);
	}

	@GetMapping("/api/orders/{id}")
	Response get(@PathVariable String id) {
		return workflow.get(id);
	}
}
