package com.example.flashsale.product;

import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductService {
	private final ProductRepository repository;
	private final Counter reductions;
	private final Counter reductionSuccesses;
	private final Counter outOfStock;
	private final Counter restorations;
	private final Timer updateDuration;
	private final long delayMs;
	private final int errorPercentage;

	public ProductService(ProductRepository repository, MeterRegistry registry,
			@Value("${flashsale.failure.inventory-delay-ms:0}") long delayMs,
			@Value("${flashsale.failure.inventory-error-percentage:0}") int errorPercentage) {
		this.repository = repository;
		this.reductions = registry.counter("flashsale.inventory.reduction.requests");
		this.reductionSuccesses = registry.counter("flashsale.inventory.reduction.successes");
		this.outOfStock = registry.counter("flashsale.inventory.out_of_stock");
		this.restorations = registry.counter("flashsale.inventory.restoration.requests");
		this.updateDuration = registry.timer("flashsale.inventory.update.duration");
		this.delayMs = delayMs;
		this.errorPercentage = errorPercentage;
	}

	@Transactional
	public Product create(ProductDtos.CreateProductRequest request) {
		return repository.save(new Product(request.name(), request.price(), request.availableQuantity()));
	}

	@Transactional(readOnly = true)
	public Product get(long id) {
		return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
	}

	@Transactional
	public Product reduce(long id, int quantity) {
		reductions.increment();
		return updateDuration.record(() -> {
			simulateFailure();
			Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
			if (quantity < 1)
				throw new InvalidQuantityException();
			if (product.getAvailableQuantity() < quantity) {
				outOfStock.increment();
				throw new OutOfStockException(id);
			}
			// Deliberately unsafe read-check-write: concurrent callers can overwrite each
			// other.
			product.setAvailableQuantity(product.getAvailableQuantity() - quantity);
			Product saved = repository.save(product);
			reductionSuccesses.increment();
			return saved;
		});
	}

	@Transactional
	public Product restore(long id, int quantity) {
		restorations.increment();
		return updateDuration.record(() -> {
			simulateFailure();
			if (quantity < 1)
				throw new InvalidQuantityException();
			Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
			product.setAvailableQuantity(product.getAvailableQuantity() + quantity);
			return repository.save(product);
		});
	}

	@Transactional
	public Product reset(long id, int quantity) {
		Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		product.setAvailableQuantity(quantity);
		return repository.save(product);
	}

	private void simulateFailure() {
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SimulatedInventoryException();
		}
		if (ThreadLocalRandom.current().nextInt(100) < errorPercentage)
			throw new SimulatedInventoryException();
	}
}
