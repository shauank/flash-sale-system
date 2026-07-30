package com.example.flashsale.order;

import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderWorkflow {
	private static final Logger log = LoggerFactory.getLogger(OrderWorkflow.class);
	private final OrderRepository repository;
	private final ProductServiceClient products;
	private final ReservationServiceClient reservations;
	private final PaymentServiceClient payments;
	private final RedisInventoryGate redisInventory;
	private final int failAfterPayment;
	private final Counter requested, confirmed, failed, outOfStock;
	private final Timer duration;

	OrderWorkflow(OrderRepository repository, ProductServiceClient products, ReservationServiceClient reservations,
			PaymentServiceClient payments, RedisInventoryGate redisInventory, MeterRegistry registry,
			@Value("${flashsale.failure.after-payment-success-percentage:0}") int failAfterPayment) {
		this.repository = repository;
		this.products = products;
		this.reservations = reservations;
		this.payments = payments;
		this.redisInventory = redisInventory;
		this.failAfterPayment = failAfterPayment;
		requested = registry.counter("flashsale.orders.requested");
		confirmed = registry.counter("flashsale.orders.confirmed");
		failed = registry.counter("flashsale.orders.failed");
		outOfStock = registry.counter("flashsale.orders.out_of_stock");
		duration = registry.timer("flashsale.order.processing.duration");
	}

	public OrderApi.Response create(OrderApi.CreateRequest request) {
		requested.increment();
		return duration.record(() -> orchestrate(request));
	}

	private OrderApi.Response orchestrate(OrderApi.CreateRequest request) {
		Order order = createPending(request);
		MDC.put("orderId", order.getOrderId());
		log.info("Order orchestration started");
		ProductServiceClient.Product product;
		try {
			log.info("Getting product");
			product = products.get(request.productId());
			BigDecimal total = product.price().multiply(BigDecimal.valueOf(request.quantity()));
			price(order, total);
			log.info("Reducing inventory");
			products.reduce(request.productId(), order.getOrderId(), request.requestId(), request.quantity());
		} catch (DownstreamConflictException e) {
			outOfStock.increment();
			fail(order, "OUT_OF_STOCK");
			return response(order, null, null, "Product is out of stock");
		} catch (DownstreamNotFoundException e) {
			fail(order, "PRODUCT_NOT_FOUND");
			return response(order, null, null, "Product was not found");
		}

		ReservationServiceClient.Reservation reservation;
		try {
			log.info("Creating reservation");
			reservation = reservations.create(order.getOrderId(), request.productId(), request.userId(),
					request.quantity());
			attachReservation(order, reservation.reservationId());
			MDC.put("reservationId", reservation.reservationId());
		} catch (RuntimeException e) {
			// Intentional weakness: inventory is not compensated when reservation creation
			// fails.
			fail(order, "RESERVATION_SERVICE_FAILURE");
			throw e;
		}

		log.info("Processing payment");
		PaymentServiceClient.Payment payment = payments.process(order.getOrderId(), request.userId(),
				order.getTotalAmount());
		attachPayment(order, payment.paymentId());
		MDC.put("paymentId", payment.paymentId());
		if ("SUCCESS".equals(payment.status())) {
			if (ThreadLocalRandom.current().nextInt(100) < failAfterPayment) {
				log.error("Simulated crash after successful payment");
				throw new AfterPaymentSuccessException();
			}
			log.info("Confirming reservation");
			ReservationServiceClient.Reservation finalReservation = reservations.confirm(reservation.reservationId());
			confirm(order);
			confirmed.increment();
			log.info("Order orchestration completed with CONFIRMED");
			return response(order, finalReservation.status(), payment.status(), "Order completed successfully");
		}

		log.info("Payment failed; starting compensation");
		String reservationStatus = "RESERVED";
		try {
			reservationStatus = reservations.cancel(reservation.reservationId()).status();
		} catch (RuntimeException e) {
			log.error("Reservation cancellation compensation failed", e);
		}
		try {
			products.restore(request.productId(), order.getOrderId(), request.requestId(), request.quantity());
		} catch (RuntimeException e) {
			log.error("Inventory restoration compensation failed", e);
		}
		fail(order, payment.failureReason() == null ? "PAYMENT_FAILED" : payment.failureReason());
		return response(order, reservationStatus, payment.status(), "Payment failed");
	}

	@Transactional
	public Order createPending(OrderApi.CreateRequest r) {
		redisInventory.reserve(r.productId(), r.quantity());
		return repository
				.save(new Order("order-" + UUID.randomUUID(), r.requestId(), r.userId(), r.productId(), r.quantity()));
	}

	@Transactional
	public void price(Order o, BigDecimal amount) {
		o.priced(amount);
		repository.save(o);
	}

	@Transactional
	public void attachReservation(Order o, String id) {
		o.reserved(id);
		repository.save(o);
	}

	@Transactional
	public void attachPayment(Order o, String id) {
		o.paid(id);
		repository.save(o);
	}

	@Transactional
	public void confirm(Order o) {
		o.confirm();
		repository.save(o);
	}

	@Transactional
	public void fail(Order o, String reason) {
		o.fail(reason);
		repository.save(o);
		failed.increment();
	}

	@Transactional(readOnly = true)
	public OrderApi.Response get(String id) {
		Order o = repository.findByOrderId(id).orElseThrow(() -> new OrderNotFoundException(id));
		return response(o, null, null, "Order retrieved");
	}

	private OrderApi.Response response(Order o, String reservationStatus, String paymentStatus, String message) {
		return new OrderApi.Response(o.getRequestId(), o.getOrderId(), o.getReservationId(), o.getPaymentId(),
				o.getStatus(), reservationStatus, paymentStatus, message, o.getFailureReason(), o.getTotalAmount(),
				o.getCreatedAt());
	}
}
