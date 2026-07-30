package com.example.flashsale.reservation;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

class ReservationNotFoundException extends RuntimeException {
	ReservationNotFoundException(String id) {
		super("Reservation " + id + " was not found");
	}
}

class InvalidReservationTransitionException extends RuntimeException {
	InvalidReservationTransitionException(Reservation.Status from, Reservation.Status to) {
		super("Invalid reservation transition from " + from + " to " + to);
	}
}

class SimulatedReservationException extends RuntimeException {
	SimulatedReservationException() {
		super("Simulated reservation failure");
	}
}

@RestControllerAdvice
class ReservationErrors {
	record ApiError(Instant timestamp, int status, String errorCode, String message, String requestId,
			String serviceName) {
	}

	@ExceptionHandler(ReservationNotFoundException.class)
	ResponseEntity<ApiError> missing(Exception e) {
		return out(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", e);
	}

	@ExceptionHandler({ InvalidReservationTransitionException.class, MethodArgumentNotValidException.class })
	ResponseEntity<ApiError> invalid(Exception e) {
		return out(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", e);
	}

	@ExceptionHandler(SimulatedReservationException.class)
	ResponseEntity<ApiError> simulated(Exception e) {
		return out(HttpStatus.SERVICE_UNAVAILABLE, "RESERVATION_FAILURE", e);
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> db(Exception e) {
		return out(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
				new RuntimeException("Database operation failed"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> other(Exception e) {
		return out(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				new RuntimeException("Unexpected internal error"));
	}

	private ResponseEntity<ApiError> out(HttpStatus s, String c, Exception e) {
		return ResponseEntity.status(s).body(
				new ApiError(Instant.now(), s.value(), c, e.getMessage(), MDC.get("requestId"), "reservation-service"));
	}
}

@org.springframework.stereotype.Component
class CorrelationIdFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String id = req.getHeader("X-Correlation-ID");
		if (id == null || id.isBlank())
			id = UUID.randomUUID().toString();
		MDC.put("correlationId", id);
		MDC.put("requestId", id);
		res.setHeader("X-Correlation-ID", id);
		long started = System.nanoTime();
		try {
			chain.doFilter(req, res);
		} finally {
			MDC.put("processingDurationMs", String.valueOf((System.nanoTime() - started) / 1_000_000));
			MDC.put("finalStatus", String.valueOf(res.getStatus()));
			log.info("Request completed method={} path={}", req.getMethod(), req.getRequestURI());
			MDC.clear();
		}
	}
}
