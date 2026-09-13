package com.example.flashsale.payment;

import static com.example.flashsale.events.FlashSaleEvents.*;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class PaymentKafkaHandlers {
	private final PaymentService service;
	private final KafkaTemplate<String, Object> kafka;

	PaymentKafkaHandlers(PaymentService service, KafkaTemplate<String, Object> kafka) {
		this.service = service;
		this.kafka = kafka;
	}

	@KafkaListener(topics = PAYMENT_REQUESTED)
	void process(PaymentRequested event) {
		MDC.put("orderId", event.orderId());
		MDC.put("userId", event.userId());
		try {
			Payment payment = service.process(new PaymentApi.Request(event.orderId(), event.userId(), event.amount()));
			MDC.put("paymentId", payment.getPaymentId());
			kafka.send(PAYMENT_COMPLETED, event.orderId(), new PaymentCompleted(event.orderId(), payment.getPaymentId(),
					payment.getStatus().name(), payment.getFailureReason()));
		} catch (RuntimeException e) {
			kafka.send(PAYMENT_COMPLETED, event.orderId(),
					new PaymentCompleted(event.orderId(), null, "FAILED", e.getMessage()));
		} finally {
			MDC.clear();
		}
	}
}
