package com.example.flashsale.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {
	public enum Status {
		PENDING, SUCCESS, FAILED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "payment_id", nullable = false, unique = true)
	private String paymentId;
	@Column(name = "order_id", nullable = false)
	private String orderId;
	@Column(name = "user_id", nullable = false)
	private String userId;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;
	@Column(name = "failure_reason")
	private String failureReason;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Payment() {
	}

	public Payment(String paymentId, String orderId, String userId, BigDecimal amount) {
		this.paymentId = paymentId;
		this.orderId = orderId;
		this.userId = userId;
		this.amount = amount;
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

	public String getPaymentId() {
		return paymentId;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getUserId() {
		return userId;
	}

	public BigDecimal getAmount() {
		return amount;
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

	public void succeed() {
		status = Status.SUCCESS;
	}

	public void fail(String reason) {
		status = Status.FAILED;
		failureReason = reason;
	}
}
