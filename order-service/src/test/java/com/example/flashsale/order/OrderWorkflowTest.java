package com.example.flashsale.order;

import static com.example.flashsale.events.FlashSaleEvents.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class OrderWorkflowTest {
	private OrderRepository repository;
	private RedisInventoryGate redisInventory;
	private KafkaTemplate<String, Object> kafka;
	private OrderWorkflow workflow;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		repository = mock(OrderRepository.class);
		redisInventory = mock(RedisInventoryGate.class);
		kafka = mock(KafkaTemplate.class);
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		workflow = new OrderWorkflow(repository, redisInventory, kafka, new SimpleMeterRegistry());
	}

	@Test
	void acceptsOrderAndRequestsInventoryReduction() {
		OrderApi.CreateRequest request = new OrderApi.CreateRequest("request-1", "user-1", 1L, 2);

		OrderApi.Response response = workflow.create(request);

		assertEquals(Order.Status.INVENTORY_PENDING, response.orderStatus());
		verify(redisInventory).reserve(1L, 2);
		verify(kafka).send(eq(INVENTORY_REDUCTION_REQUESTED), eq(response.orderId()),
				any(InventoryReductionRequested.class));
	}

	@Test
	void inventorySuccessRequestsReservation() {
		Order order = order();
		when(repository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

		workflow.inventoryCompleted(new InventoryReductionCompleted(order.getOrderId(), order.getRequestId(),
				order.getUserId(), order.getProductId(), order.getQuantity(), true, new BigDecimal("10.00"), null));

		assertEquals(Order.Status.RESERVATION_PENDING, order.getStatus());
		assertEquals(new BigDecimal("20.00"), order.getTotalAmount());
		verify(kafka).send(eq(RESERVATION_REQUESTED), eq(order.getOrderId()), any(ReservationRequested.class));
	}

	@Test
	void reservationSuccessRequestsPayment() {
		Order order = order();
		order.reservationPending(new BigDecimal("20.00"));
		when(repository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

		workflow.reservationCompleted(new ReservationCompleted(order.getOrderId(), "reservation-1",
				order.getProductId(), order.getUserId(), order.getQuantity(), "CREATE", true, "RESERVED", null));

		assertEquals(Order.Status.PAYMENT_PENDING, order.getStatus());
		assertEquals("reservation-1", order.getReservationId());
		verify(kafka).send(eq(PAYMENT_REQUESTED), eq(order.getOrderId()), any(PaymentRequested.class));
	}

	@Test
	void paymentSuccessRequestsReservationConfirmation() {
		Order order = order();
		order.reserved("reservation-1");
		order.paymentPending();
		when(repository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

		workflow.paymentCompleted(new PaymentCompleted(order.getOrderId(), "payment-1", "SUCCESS", null));

		assertEquals(Order.Status.CONFIRMATION_PENDING, order.getStatus());
		assertEquals("payment-1", order.getPaymentId());
		verify(kafka).send(eq(RESERVATION_CONFIRMATION_REQUESTED), eq(order.getOrderId()),
				any(ReservationConfirmationRequested.class));
	}

	@Test
	void reservationConfirmationCompletesOrder() {
		Order order = order();
		order.reserved("reservation-1");
		order.confirmationPending();
		when(repository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

		workflow.reservationCompleted(new ReservationCompleted(order.getOrderId(), "reservation-1",
				order.getProductId(), order.getUserId(), order.getQuantity(), "CONFIRM", true, "CONFIRMED", null));

		assertEquals(Order.Status.CONFIRMED, order.getStatus());
	}

	@Test
	void paymentFailureRequestsCompensation() {
		Order order = order();
		order.reserved("reservation-1");
		order.paymentPending();
		when(repository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));

		workflow.paymentCompleted(new PaymentCompleted(order.getOrderId(), "payment-1", "FAILED", "DECLINED"));

		assertEquals(Order.Status.FAILED, order.getStatus());
		verify(kafka).send(eq(RESERVATION_CANCELLATION_REQUESTED), eq(order.getOrderId()),
				any(ReservationCancellationRequested.class));
		verify(kafka).send(eq(INVENTORY_RESTORATION_REQUESTED), eq(order.getOrderId()),
				any(InventoryRestorationRequested.class));
	}

	@Test
	void redisRejectionPreventsOrderCreation() {
		doThrow(new RedisInventoryRejectedException(1L, "insufficient quantity")).when(redisInventory).reserve(1L, 2);

		assertThrows(RedisInventoryRejectedException.class,
				() -> workflow.create(new OrderApi.CreateRequest("request-1", "user-1", 1L, 2)));
		verify(repository, never()).save(any());
		verifyNoInteractions(kafka);
	}

	private Order order() {
		return new Order("order-1", "request-1", "user-1", 1L, 2);
	}
}
