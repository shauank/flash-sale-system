package com.example.flashsale.reservation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {
	public enum Status {
		RESERVED, CONFIRMED, CANCELLED, EXPIRED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "reservation_id", nullable = false, unique = true)
	private String reservationId;
	@Column(name = "order_id", nullable = false)
	private String orderId;
	@Column(name = "product_id", nullable = false)
	private Long productId;
	@Column(name = "user_id", nullable = false)
	private String userId;
	@Column(nullable = false)
	private int quantity;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;
	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Reservation() {
	}

	public Reservation(String reservationId, String orderId, Long productId, String userId, int quantity) {
		this.reservationId = reservationId;
		this.orderId = orderId;
		this.productId = productId;
		this.userId = userId;
		this.quantity = quantity;
		this.status = Status.RESERVED;
		this.expiresAt = Instant.now().plusSeconds(900);
	}

	@PrePersist
	void created() {
		createdAt = updatedAt = Instant.now();
	}

	@PreUpdate
	void updated() {
		updatedAt = Instant.now();
	}

	public String getReservationId() {
		return reservationId;
	}

	public String getOrderId() {
		return orderId;
	}

	public Long getProductId() {
		return productId;
	}

	public String getUserId() {
		return userId;
	}

	public int getQuantity() {
		return quantity;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void transition(Status target) {
		if (status != Status.RESERVED || (target != Status.CONFIRMED && target != Status.CANCELLED))
			throw new InvalidReservationTransitionException(status, target);
		status = target;
	}
}
