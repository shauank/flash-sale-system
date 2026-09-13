package com.example.flashsale.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {
	public enum Status {
		PENDING, INVENTORY_PENDING, RESERVATION_PENDING, RESERVED, PAYMENT_PENDING, CONFIRMATION_PENDING, CONFIRMED,
		FAILED, CANCELLED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "order_id", nullable = false, unique = true)
	private String orderId;
	@Column(name = "request_id", nullable = false)
	private String requestId;
	@Column(name = "user_id", nullable = false)
	private String userId;
	@Column(name = "product_id", nullable = false)
	private Long productId;
	@Column(name = "reservation_id")
	private String reservationId;
	@Column(name = "payment_id")
	private String paymentId;
	@Column(nullable = false)
	private int quantity;
	@Column(name = "total_amount", precision = 19, scale = 2)
	private BigDecimal totalAmount;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;
	@Column(name = "failure_reason")
	private String failureReason;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Order() {
	}

	public Order(String orderId, String requestId, String userId, Long productId, int quantity) {
		this.orderId = orderId;
		this.requestId = requestId;
		this.userId = userId;
		this.productId = productId;
		this.quantity = quantity;
		this.status = Status.PENDING;
	}

	@PrePersist
	void created() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void updated() {
		updatedAt = Instant.now();
	}

	public String getOrderId() {
		return orderId;
	}

	public String getRequestId() {
		return requestId;
	}

	public String getUserId() {
		return userId;
	}

	public Long getProductId() {
		return productId;
	}

	public String getReservationId() {
		return reservationId;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public Status getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void priced(BigDecimal amount) {
		totalAmount = amount;
	}

	public void inventoryPending() {
		status = Status.INVENTORY_PENDING;
	}

	public void reservationPending(BigDecimal amount) {
		totalAmount = amount;
		status = Status.RESERVATION_PENDING;
	}

	public void reserved(String id) {
		reservationId = id;
		status = Status.RESERVED;
	}

	public void paymentPending() {
		status = Status.PAYMENT_PENDING;
	}

	public void paid(String id) {
		paymentId = id;
	}

	public void confirmationPending() {
		status = Status.CONFIRMATION_PENDING;
	}

	public void confirm() {
		status = Status.CONFIRMED;
	}

	public void fail(String reason) {
		status = Status.FAILED;
		failureReason = reason;
	}
}
