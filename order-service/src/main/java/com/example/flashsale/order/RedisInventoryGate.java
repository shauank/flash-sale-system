package com.example.flashsale.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Configuration
class RedisInventoryConfiguration {
	@Bean
	RedisScript<Long> reserveProductScript() {
		return RedisScript.of(new ClassPathResource("redis/reserve-product.lua"), Long.class);
	}
}

@Component
class RedisInventoryGate {
	private static final long ACCEPTED = 1;
	private static final long INSUFFICIENT_QUANTITY = 0;
	private static final long QUANTITY_NOT_INITIALIZED = -1;

	private final StringRedisTemplate redis;
	private final RedisScript<Long> reserveProductScript;
	private final String productKey;
	private final Counter accepted;
	private final Counter rejected;
	private final Counter unavailable;

	RedisInventoryGate(StringRedisTemplate redis, RedisScript<Long> reserveProductScript,
			@Value("${flashsale.redis.product-key:product}") String productKey, MeterRegistry registry) {
		this.redis = redis;
		this.reserveProductScript = reserveProductScript;
		this.productKey = productKey;
		this.accepted = registry.counter("flashsale.redis.inventory.accepted");
		this.rejected = registry.counter("flashsale.redis.inventory.rejected");
		this.unavailable = registry.counter("flashsale.redis.inventory.unavailable");
	}

	void reserve(long productId, int requestedQuantity) {
		final Long result;
		try {
			result = redis.execute(reserveProductScript, List.of(productKey), String.valueOf(productId),
					String.valueOf(requestedQuantity));
		} catch (DataAccessException e) {
			unavailable.increment();
			throw new RedisInventoryUnavailableException(e);
		}

		if (result != null && result == ACCEPTED) {
			accepted.increment();
			return;
		}

		rejected.increment();
		if (result != null && result == QUANTITY_NOT_INITIALIZED) {
			throw new RedisInventoryRejectedException(productId, "quantity is not initialized");
		}
		if (result != null && result == INSUFFICIENT_QUANTITY) {
			throw new RedisInventoryRejectedException(productId, "insufficient quantity");
		}
		throw new RedisInventoryUnavailableException(
				new IllegalStateException("Unexpected Redis script result: " + result));
	}
}
