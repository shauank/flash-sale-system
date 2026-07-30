package com.example.flashsale.product;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

class ProductNotFoundException extends RuntimeException {
	ProductNotFoundException(long id) {
		super("Product " + id + " was not found");
	}
}

class OutOfStockException extends RuntimeException {
	OutOfStockException(long id) {
		super("Product " + id + " is out of stock");
	}
}

class InvalidQuantityException extends RuntimeException {
	InvalidQuantityException() {
		super("Quantity must be greater than zero");
	}
}

class SimulatedInventoryException extends RuntimeException {
	SimulatedInventoryException() {
		super("Simulated inventory failure");
	}
}

@RestControllerAdvice
class ProductExceptionHandler {
	record ApiError(Instant timestamp, int status, String errorCode, String message, String requestId,
			String serviceName) {
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ApiError> notFound(Exception e) {
		return response(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", e);
	}

	@ExceptionHandler(OutOfStockException.class)
	ResponseEntity<ApiError> outOfStock(Exception e) {
		return response(HttpStatus.CONFLICT, "OUT_OF_STOCK", e);
	}

	@ExceptionHandler({ InvalidQuantityException.class, MethodArgumentNotValidException.class })
	ResponseEntity<ApiError> invalid(Exception e) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e);
	}

	@ExceptionHandler(SimulatedInventoryException.class)
	ResponseEntity<ApiError> simulated(Exception e) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "INVENTORY_FAILURE", e);
	}

	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<ApiError> database(Exception e) {
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR",
				new RuntimeException("Database operation failed"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception e) {
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
				new RuntimeException("Unexpected internal error"));
	}

	private ResponseEntity<ApiError> response(HttpStatus status, String code, Exception e) {
		return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, e.getMessage(),
				MDC.get("requestId"), "product-service"));
	}
}
