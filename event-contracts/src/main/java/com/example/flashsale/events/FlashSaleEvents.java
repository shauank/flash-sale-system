package com.example.flashsale.events;

import java.math.BigDecimal;

public final class FlashSaleEvents {
	private FlashSaleEvents() {
	}

	public static final String INVENTORY_REDUCTION_REQUESTED = "flashsale.inventory.reduction.requested";
	public static final String INVENTORY_REDUCTION_COMPLETED = "flashsale.inventory.reduction.completed";
	public static final String INVENTORY_RESTORATION_REQUESTED = "flashsale.inventory.restoration.requested";
	public static final String RESERVATION_REQUESTED = "flashsale.reservation.requested";
	public static final String RESERVATION_COMPLETED = "flashsale.reservation.completed";
	public static final String RESERVATION_CONFIRMATION_REQUESTED = "flashsale.reservation.confirmation.requested";
	public static final String RESERVATION_CANCELLATION_REQUESTED = "flashsale.reservation.cancellation.requested";
	public static final String PAYMENT_REQUESTED = "flashsale.payment.requested";
	public static final String PAYMENT_COMPLETED = "flashsale.payment.completed";

	public record InventoryReductionRequested(String orderId, String requestId, String userId, long productId,
			int quantity) {
	}

	public record InventoryReductionCompleted(String orderId, String requestId, String userId, long productId,
			int quantity, boolean successful, BigDecimal unitPrice, String failureReason) {
	}

	public record InventoryRestorationRequested(String orderId, String requestId, long productId, int quantity,
			String reason) {
	}

	public record ReservationRequested(String orderId, String requestId, String userId, long productId, int quantity) {
	}

	public record ReservationCompleted(String orderId, String reservationId, long productId, String userId,
			int quantity, String operation, boolean successful, String status, String failureReason) {
	}

	public record ReservationConfirmationRequested(String orderId, String reservationId) {
	}

	public record ReservationCancellationRequested(String orderId, String reservationId, String reason) {
	}

	public record PaymentRequested(String orderId, String userId, BigDecimal amount) {
	}

	public record PaymentCompleted(String orderId, String paymentId, String status, String failureReason) {
	}
}
