package com.example.flashsale.order;

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

class OrderNotFoundException extends RuntimeException {
	OrderNotFoundException(String id) {
		super("Order " + id + " was not found");
	}
}

class DownstreamFailureException extends RuntimeException {
	DownstreamFailureException(String service, Throwable cause) {
		super(service + " call failed", cause);
	}
}

class DownstreamTimeoutException extends DownstreamFailureException {
	DownstreamTimeoutException(String service, Throwable cause) {
		super(service + " timed out", cause);
	}
}

class DownstreamUnavailableException extends DownstreamFailureException {
	DownstreamUnavailableException(String service, Throwable cause) {
		super(service + " is unavailable", cause);
	}
}

class DownstreamNotFoundException extends DownstreamFailureException {
	DownstreamNotFoundException(String service, Throwable cause) {
		super(service + " resource not found", cause);
	}
}

class DownstreamConflictException extends DownstreamFailureException {
	DownstreamConflictException(String service, Throwable cause) {
		super(service + " rejected the operation", cause);
	}
}

class AfterPaymentSuccessException extends RuntimeException {
	AfterPaymentSuccessException() {
		super("Simulated failure after successful payment");
	}
}

class RedisInventoryRejectedException extends RuntimeException {
	RedisInventoryRejectedException(long productId, String reason) {
		super("Product " + productId + " is unavailable in Redis: " + reason);
	}
}

class RedisInventoryUnavailableException extends RuntimeException {
	RedisInventoryUnavailableException(Throwable cause) {
		super("Redis inventory check is unavailable", cause);
	}
}

@RestControllerAdvice
class OrderErrors {
	record ApiError(Instant timestamp, int status, String errorCode, String message, String requestId,
			String serviceName) {
	}

	@ExceptionHandler(OrderNotFoundException.class)
	ResponseEntity<ApiError> missing(Exception e) {
		return out(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalid(Exception e) {
		return out(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid order request");
	}

	@ExceptionHandler(DownstreamTimeoutException.class)
	ResponseEntity<ApiError> timeout(Exception e) {
		return out(HttpStatus.GATEWAY_TIMEOUT, "SERVICE_TIMEOUT", e.getMessage());
	}

	@ExceptionHandler(DownstreamUnavailableException.class)
	ResponseEntity<ApiError> unavailable(Exception e) {
		return out(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", e.getMessage());
	}

	@ExceptionHandler(DownstreamFailureException.class)
	ResponseEntity<ApiError> downstream(Exception e) {
		return out(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_FAILURE", e.getMessage());
	}

	@ExceptionHandler(AfterPaymentSuccessException.class)
	ResponseEntity<ApiError> afterPayment(Exception e) {
		return out(HttpStatus.INTERNAL_SERVER_ERROR, "AFTER_PAYMENT_SUCCESS_FAILURE", e.getMessage());
	}

	@ExceptionHandler(RedisInventoryRejectedException.class)
	ResponseEntity<ApiError> redisInventoryRejected(Exception e) {
		return out(HttpStatus.CONFLICT, "REDIS_INVENTORY_REJECTED", e.getMessage());
	}

	@ExceptionHandler(RedisInventoryUnavailableException.class)
	ResponseEntity<ApiError> redisUnavailable(Exception e) {
		return out(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_INVENTORY_UNAVAILABLE", e.getMessage());
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> db(Exception e) {
		return out(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", "Database operation failed");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> other(Exception e) {
		return out(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected internal error");
	}

	private ResponseEntity<ApiError> out(HttpStatus s, String c, String m) {
		return ResponseEntity.status(s)
				.body(new ApiError(Instant.now(), s.value(), c, m, MDC.get("requestId"), "order-service"));
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
