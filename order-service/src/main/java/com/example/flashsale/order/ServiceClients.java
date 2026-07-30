package com.example.flashsale.order;

import io.micrometer.core.instrument.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Configuration
class RestClientConfiguration {
	@Bean
	RestClient.Builder restClientBuilder(@Value("${services.http.connect-timeout-ms:1000}") int connect,
			@Value("${services.http.read-timeout-ms:5000}") int read) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(connect));
		factory.setReadTimeout(Duration.ofMillis(read));
		return RestClient.builder().requestFactory(factory);
	}
}

@Component
class ProductServiceClient {
	record Product(Long id, String name, BigDecimal price, int availableQuantity) {
	}

	record Inventory(Long productId, int availableQuantity) {
	}

	private final RestClient client;
	private final Timer duration;

	ProductServiceClient(RestClient.Builder builder, @Value("${services.product-service.base-url}") String url,
			MeterRegistry registry) {
		client = builder.clone().baseUrl(url).build();
		duration = registry.timer("flashsale.client.product.duration");
	}

	Product get(long id) {
		return call(() -> client.get().uri("/api/products/{id}", id).header("X-Correlation-ID", correlation())
				.retrieve().body(Product.class));
	}

	void reduce(long id, String orderId, String requestId, int quantity) {
		call(() -> client.post().uri("/internal/products/{id}/inventory/reduce", id)
				.header("X-Correlation-ID", correlation())
				.body(Map.of("orderId", orderId, "requestId", requestId, "quantity", quantity)).retrieve()
				.body(Inventory.class));
	}

	void restore(long id, String orderId, String requestId, int quantity) {
		call(() -> client.post().uri("/internal/products/{id}/inventory/restore", id)
				.header("X-Correlation-ID", correlation()).body(Map.of("orderId", orderId, "requestId", requestId,
						"quantity", quantity, "reason", "PAYMENT_FAILED"))
				.retrieve().body(Inventory.class));
	}

	private <T> T call(java.util.function.Supplier<T> action) {
		return duration.record(() -> ClientSupport.call("product-service", action));
	}

	private String correlation() {
		return ClientSupport.correlation();
	}
}

@Component
class ReservationServiceClient {
	record Reservation(String reservationId, String orderId, Long productId, String userId, int quantity,
			String status) {
	}

	private final RestClient client;
	private final Timer duration;

	ReservationServiceClient(RestClient.Builder builder, @Value("${services.reservation-service.base-url}") String url,
			MeterRegistry registry) {
		client = builder.clone().baseUrl(url).build();
		duration = registry.timer("flashsale.client.reservation.duration");
	}

	Reservation create(String orderId, long productId, String userId, int quantity) {
		return call(() -> client.post().uri("/internal/reservations")
				.header("X-Correlation-ID", ClientSupport.correlation())
				.body(Map.of("orderId", orderId, "productId", productId, "userId", userId, "quantity", quantity))
				.retrieve().body(Reservation.class));
	}

	Reservation confirm(String id) {
		return action(id, "confirm", null);
	}

	Reservation cancel(String id) {
		return action(id, "cancel", Map.of("reason", "PAYMENT_FAILED"));
	}

	private Reservation action(String id, String verb, Object body) {
		return call(() -> {
			RestClient.RequestBodySpec spec = client.post().uri("/internal/reservations/{id}/" + verb, id)
					.header("X-Correlation-ID", ClientSupport.correlation());
			if (body != null)
				spec.body(body);
			return spec.retrieve().body(Reservation.class);
		});
	}

	private <T> T call(java.util.function.Supplier<T> action) {
		return duration.record(() -> ClientSupport.call("reservation-service", action));
	}
}

@Component
class PaymentServiceClient {
	record Payment(String paymentId, String orderId, String userId, BigDecimal amount, String status,
			String failureReason) {
	}

	private final RestClient client;
	private final Timer duration;

	PaymentServiceClient(RestClient.Builder builder, @Value("${services.payment-service.base-url}") String url,
			MeterRegistry registry) {
		client = builder.clone().baseUrl(url).build();
		duration = registry.timer("flashsale.client.payment.duration");
	}

	Payment process(String orderId, String userId, BigDecimal amount) {
		return duration.record(() -> ClientSupport.call("payment-service",
				() -> client.post().uri("/internal/payments").header("X-Correlation-ID", ClientSupport.correlation())
						.body(Map.of("orderId", orderId, "userId", userId, "amount", amount)).retrieve()
						.body(Payment.class)));
	}
}

final class ClientSupport {
	private ClientSupport() {
	}

	static String correlation() {
		String id = MDC.get("correlationId");
		return id == null ? "unknown" : id;
	}

	static <T> T call(String service, java.util.function.Supplier<T> action) {
		try {
			return action.get();
		} catch (ResourceAccessException e) {
			String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
			if (message.contains("timed out"))
				throw new DownstreamTimeoutException(service, e);
			throw new DownstreamUnavailableException(service, e);
		} catch (HttpClientErrorException.NotFound e) {
			throw new DownstreamNotFoundException(service, e);
		} catch (HttpClientErrorException.Conflict e) {
			throw new DownstreamConflictException(service, e);
		} catch (RestClientResponseException e) {
			throw new DownstreamFailureException(service, e);
		}
	}
}
