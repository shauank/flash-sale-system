package com.example.flashsale.reservation;

import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReservationService {
	private final ReservationRepository repository;
	private final Counter created, confirmed, cancelled;
	private final Timer duration;
	private final long delayMs;
	private final int errorPercentage;

	ReservationService(ReservationRepository repository, MeterRegistry registry,
			@Value("${flashsale.failure.reservation-delay-ms:0}") long delayMs,
			@Value("${flashsale.failure.reservation-error-percentage:0}") int errorPercentage) {
		this.repository = repository;
		this.created = registry.counter("flashsale.reservations.created");
		this.confirmed = registry.counter("flashsale.reservations.confirmed");
		this.cancelled = registry.counter("flashsale.reservations.cancelled");
		this.duration = registry.timer("flashsale.reservation.operation.duration");
		this.delayMs = delayMs;
		this.errorPercentage = errorPercentage;
	}

	@Transactional
	public Reservation create(ReservationApi.CreateRequest r) {
		return duration.record(() -> {
			simulate();
			Reservation saved = repository.save(new Reservation("reservation-" + UUID.randomUUID(), r.orderId(),
					r.productId(), r.userId(), r.quantity()));
			created.increment();
			return saved;
		});
	}

	@Transactional
	public Reservation confirm(String id) {
		return duration.record(() -> {
			simulate();
			Reservation r = getEntity(id);
			r.transition(Reservation.Status.CONFIRMED);
			confirmed.increment();
			return r;
		});
	}

	@Transactional
	public Reservation cancel(String id) {
		return duration.record(() -> {
			simulate();
			Reservation r = getEntity(id);
			r.transition(Reservation.Status.CANCELLED);
			cancelled.increment();
			return r;
		});
	}

	@Transactional(readOnly = true)
	public Reservation get(String id) {
		return getEntity(id);
	}

	private Reservation getEntity(String id) {
		return repository.findByReservationId(id).orElseThrow(() -> new ReservationNotFoundException(id));
	}

	private void simulate() {
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SimulatedReservationException();
		}
		if (ThreadLocalRandom.current().nextInt(100) < errorPercentage)
			throw new SimulatedReservationException();
	}
}
