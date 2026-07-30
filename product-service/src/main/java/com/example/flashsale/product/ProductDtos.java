package com.example.flashsale.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {
	private ProductDtos() {
	}

	public record CreateProductRequest(@NotBlank String name, @NotNull @DecimalMin("0.01") BigDecimal price,
			@Min(0) int availableQuantity) {
	}

	public record InventoryChangeRequest(@NotBlank String orderId, @NotBlank String requestId, @Min(1) int quantity,
			String reason) {
	}

	public record ProductResponse(Long id, String name, BigDecimal price, int availableQuantity, Instant createdAt,
			Instant updatedAt) {
		static ProductResponse from(Product p) {
			return new ProductResponse(p.getId(), p.getName(), p.getPrice(), p.getAvailableQuantity(), p.getCreatedAt(),
					p.getUpdatedAt());
		}
	}

	public record InventoryResponse(Long productId, int availableQuantity) {
	}
}
