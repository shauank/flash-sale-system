package com.example.flashsale.reservation;

import static com.example.flashsale.events.FlashSaleEvents.*;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class ReservationKafkaHandlers {
	private final ReservationService service;
	private final KafkaTemplate<String, Object> kafka;

	ReservationKafkaHandlers(ReservationService service, KafkaTemplate<String, Object> kafka) {
		this.service = service;
		this.kafka = kafka;
	}

	@KafkaListener(topics = RESERVATION_REQUESTED)
	void create(ReservationRequested event) {
		MDC.put("orderId", event.orderId());
		try {
			Reservation reservation = service.create(new ReservationApi.CreateRequest(event.orderId(), event.productId(),
					event.userId(), event.quantity()));
			publish(event.orderId(), reservation, "CREATE", true, null);
		} catch (RuntimeException e) {
			kafka.send(RESERVATION_COMPLETED, event.orderId(), new ReservationCompleted(event.orderId(), null,
					event.productId(), event.userId(), event.quantity(), "CREATE", false, null, e.getMessage()));
		} finally {
			MDC.clear();
		}
	}

	@KafkaListener(topics = RESERVATION_CONFIRMATION_REQUESTED)
	void confirm(ReservationConfirmationRequested event) {
		MDC.put("orderId", event.orderId());
		MDC.put("reservationId", event.reservationId());
		try {
			publish(event.orderId(), service.confirm(event.reservationId()), "CONFIRM", true, null);
		} catch (RuntimeException e) {
			kafka.send(RESERVATION_COMPLETED, event.orderId(), new ReservationCompleted(event.orderId(),
					event.reservationId(), 0, null, 0, "CONFIRM", false, null, e.getMessage()));
		} finally {
			MDC.clear();
		}
	}

	@KafkaListener(topics = RESERVATION_CANCELLATION_REQUESTED)
	void cancel(ReservationCancellationRequested event) {
		MDC.put("orderId", event.orderId());
		MDC.put("reservationId", event.reservationId());
		try {
			publish(event.orderId(), service.cancel(event.reservationId()), "CANCEL", true, null);
		} catch (RuntimeException e) {
			kafka.send(RESERVATION_COMPLETED, event.orderId(), new ReservationCompleted(event.orderId(),
					event.reservationId(), 0, null, 0, "CANCEL", false, null, e.getMessage()));
		} finally {
			MDC.clear();
		}
	}

	private void publish(String orderId, Reservation reservation, String operation, boolean successful,
			String failureReason) {
		kafka.send(RESERVATION_COMPLETED, orderId,
				new ReservationCompleted(orderId, reservation.getReservationId(), reservation.getProductId(),
						reservation.getUserId(), reservation.getQuantity(), operation, successful,
						reservation.getStatus().name(), failureReason));
	}
}
