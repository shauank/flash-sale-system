package com.example.flashsale.order;

import static com.example.flashsale.events.FlashSaleEvents.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWorkflow {
	private static final Logger log = LoggerFactory.getLogger(OrderWorkflow.class);

	private final OrderRepository repository;
	private final RedisInventoryGate redisInventory;
	private final KafkaTemplate<String, Object> kafka;
	private final Counter requested;
	private final Counter confirmed;
	private final Counter failed;
	private final Counter outOfStock;
	private final Timer duration;

	OrderWorkflow(OrderRepository repository, RedisInventoryGate redisInventory, KafkaTemplate<String, Object> kafka,
			MeterRegistry registry) {
		this.repository = repository;
		this.redisInventory = redisInventory;
		this.kafka = kafka;
		this.requested = registry.counter("flashsale.orders.requested");
		this.confirmed = registry.counter("flashsale.orders.confirmed");
		this.failed = registry.counter("flashsale.orders.failed");
		this.outOfStock = registry.counter("flashsale.orders.out_of_stock");
		this.duration = registry.timer("flashsale.order.processing.duration");
	}

	@Transactional
	public OrderApi.Response create(OrderApi.CreateRequest request) {
		requested.increment();
		return duration.record(() -> {
			String orderId = "order-" + UUID.randomUUID();
			redisInventory.reserve(request.productId(), request.quantity());

			Order order = new Order(orderId, request.requestId(), request.userId(), request.productId(),
					request.quantity());
			order.inventoryPending();
			repository.save(order);

			kafka.send(INVENTORY_REDUCTION_REQUESTED, orderId,
					new InventoryReductionRequested(orderId, request.requestId(), request.userId(), request.productId(),
							request.quantity()));
			MDC.put("orderId", orderId);
			log.info("Order accepted; inventory reduction requested");
			return response(order, null, null, "Order accepted for asynchronous processing");
		});
	}

	@KafkaListener(topics = INVENTORY_REDUCTION_COMPLETED)
	@Transactional
	public void inventoryCompleted(InventoryReductionCompleted event) {
		withOrderContext(event.orderId(), () -> {
			Order order = order(event.orderId());
			if (!event.successful()) {
				outOfStock.increment();
				fail(order, event.failureReason() == null ? "INVENTORY_REJECTED" : event.failureReason());
				return;
			}

			BigDecimal total = event.unitPrice().multiply(BigDecimal.valueOf(event.quantity()));
			order.reservationPending(total);
			repository.save(order);
			kafka.send(RESERVATION_REQUESTED, order.getOrderId(),
					new ReservationRequested(order.getOrderId(), order.getRequestId(), order.getUserId(),
							order.getProductId(), order.getQuantity()));
			log.info("Inventory reduced; reservation requested");
		});
	}

	@KafkaListener(topics = RESERVATION_COMPLETED)
	@Transactional
	public void reservationCompleted(ReservationCompleted event) {
		withOrderContext(event.orderId(), () -> {
			Order order = order(event.orderId());
			if (!event.successful()) {
				fail(order, event.failureReason() == null ? "RESERVATION_FAILED" : event.failureReason());
				return;
			}

			if ("CREATE".equals(event.operation())) {
				order.reserved(event.reservationId());
				order.paymentPending();
				repository.save(order);
				kafka.send(PAYMENT_REQUESTED, order.getOrderId(),
						new PaymentRequested(order.getOrderId(), order.getUserId(), order.getTotalAmount()));
				log.info("Reservation created; payment requested");
			} else if ("CONFIRM".equals(event.operation())) {
				order.confirm();
				repository.save(order);
				confirmed.increment();
				log.info("Reservation confirmed; order confirmed");
			}
		});
	}

	@KafkaListener(topics = PAYMENT_COMPLETED)
	@Transactional
	public void paymentCompleted(PaymentCompleted event) {
		withOrderContext(event.orderId(), () -> {
			Order order = order(event.orderId());
			if (event.paymentId() != null)
				order.paid(event.paymentId());

			if ("SUCCESS".equals(event.status())) {
				order.confirmationPending();
				repository.save(order);
				kafka.send(RESERVATION_CONFIRMATION_REQUESTED, order.getOrderId(),
						new ReservationConfirmationRequested(order.getOrderId(), order.getReservationId()));
				log.info("Payment succeeded; reservation confirmation requested");
				return;
			}

			fail(order, event.failureReason() == null ? "PAYMENT_FAILED" : event.failureReason());
			kafka.send(RESERVATION_CANCELLATION_REQUESTED, order.getOrderId(),
					new ReservationCancellationRequested(order.getOrderId(), order.getReservationId(), "PAYMENT_FAILED"));
			kafka.send(INVENTORY_RESTORATION_REQUESTED, order.getOrderId(),
					new InventoryRestorationRequested(order.getOrderId(), order.getRequestId(), order.getProductId(),
							order.getQuantity(), "PAYMENT_FAILED"));
			log.info("Payment failed; asynchronous compensation requested");
		});
	}

	@Transactional(readOnly = true)
	public OrderApi.Response get(String id) {
		return response(order(id), null, null, "Order retrieved");
	}

	private Order order(String id) {
		return repository.findByOrderId(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	private void fail(Order order, String reason) {
		order.fail(reason);
		repository.save(order);
		failed.increment();
		log.info("Order failed reason={}", reason);
	}

	private OrderApi.Response response(Order order, String reservationStatus, String paymentStatus, String message) {
		return new OrderApi.Response(order.getRequestId(), order.getOrderId(), order.getReservationId(),
				order.getPaymentId(), order.getStatus(), reservationStatus, paymentStatus, message,
				order.getFailureReason(), order.getTotalAmount(), order.getCreatedAt());
	}

	private void withOrderContext(String orderId, Runnable action) {
		MDC.put("orderId", orderId);
		try {
			action.run();
		} finally {
			MDC.remove("orderId");
		}
	}
}
