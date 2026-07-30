package com.example.flashsale.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
public class ReservationApi {
	record CreateRequest(@NotBlank String orderId, @NotNull Long productId, @NotBlank String userId,
			@Min(1) int quantity) {
	}

	record CancelRequest(String reason) {
	}

	record Response(String reservationId, String orderId, Long productId, String userId, int quantity,
			Reservation.Status status, Instant expiresAt, Instant createdAt, Instant updatedAt) {
		static Response from(Reservation r) {
			return new Response(r.getReservationId(), r.getOrderId(), r.getProductId(), r.getUserId(), r.getQuantity(),
					r.getStatus(), r.getExpiresAt(), r.getCreatedAt(), r.getUpdatedAt());
		}
	}

	private final ReservationService service;

	ReservationApi(ReservationService service) {
		this.service = service;
	}

	@PostMapping("/internal/reservations")
	@ResponseStatus(HttpStatus.CREATED)
	Response create(@Valid @RequestBody CreateRequest request) {
		MDC.put("orderId", request.orderId());
		MDC.put("userId", request.userId());
		return Response.from(service.create(request));
	}

	@PostMapping("/internal/reservations/{id}/confirm")
	Response confirm(@PathVariable String id) {
		MDC.put("reservationId", id);
		return Response.from(service.confirm(id));
	}

	@PostMapping("/internal/reservations/{id}/cancel")
	Response cancel(@PathVariable String id, @RequestBody(required = false) CancelRequest request) {
		MDC.put("reservationId", id);
		return Response.from(service.cancel(id));
	}

	@GetMapping("/api/reservations/{id}")
	Response get(@PathVariable String id) {
		return Response.from(service.get(id));
	}
}
