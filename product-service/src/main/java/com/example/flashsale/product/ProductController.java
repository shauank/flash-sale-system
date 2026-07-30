package com.example.flashsale.product;

import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static com.example.flashsale.product.ProductDtos.*;

@RestController
public class ProductController {
	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping("/api/products")
	@ResponseStatus(HttpStatus.CREATED)
	ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
		return ProductResponse.from(service.create(request));
	}

	@GetMapping("/api/products/{id}")
	ProductResponse get(@PathVariable long id) {
		return ProductResponse.from(service.get(id));
	}

	@GetMapping("/api/products/{id}/inventory")
	InventoryResponse inventory(@PathVariable long id) {
		Product p = service.get(id);
		return new InventoryResponse(p.getId(), p.getAvailableQuantity());
	}

	@PostMapping("/internal/products/{id}/inventory/reduce")
	InventoryResponse reduce(@PathVariable long id, @Valid @RequestBody InventoryChangeRequest request) {
		MDC.put("orderId", request.orderId());
		MDC.put("requestId", request.requestId());
		Product p = service.reduce(id, request.quantity());
		return new InventoryResponse(p.getId(), p.getAvailableQuantity());
	}

	@PostMapping("/internal/products/{id}/inventory/restore")
	InventoryResponse restore(@PathVariable long id, @Valid @RequestBody InventoryChangeRequest request) {
		MDC.put("orderId", request.orderId());
		MDC.put("requestId", request.requestId());
		Product p = service.restore(id, request.quantity());
		return new InventoryResponse(p.getId(), p.getAvailableQuantity());
	}
}

@RestController
@Profile("dev")
class ProductTestController {
	private final ProductService service;

	ProductTestController(ProductService service) {
		this.service = service;
	}

	@PostMapping("/internal/test/reset")
	ProductDtos.InventoryResponse reset(@RequestParam(defaultValue = "100") int quantity) {
		Product p = service.reset(1, quantity);
		return new ProductDtos.InventoryResponse(p.getId(), p.getAvailableQuantity());
	}
}
