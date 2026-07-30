package com.example.flashsale.payment;

import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
	private final PaymentRepository repository;
	private final long delayMs;
	private final int failurePercentage;
	private final Counter requested, succeeded, failed;
	private final Timer duration;

	PaymentService(PaymentRepository repository, MeterRegistry registry,
			@Value("${flashsale.payment.delay-ms:500}") long delayMs,
			@Value("${flashsale.payment.failure-percentage:10}") int failurePercentage) {
		this.repository = repository;
		this.delayMs = delayMs;
		this.failurePercentage = failurePercentage;
		requested = registry.counter("flashsale.payments.requested");
		succeeded = registry.counter("flashsale.payments.succeeded");
		failed = registry.counter("flashsale.payments.failed");
		duration = registry.timer("flashsale.payment.processing.duration");
	}

	@Transactional
	public Payment process(PaymentApi.Request request) {
		requested.increment();
		return duration.record(() -> {
			Payment payment = repository.saveAndFlush(
					new Payment("payment-" + UUID.randomUUID(), request.orderId(), request.userId(), request.amount()));
			// Deliberately blocks the request thread while a DB transaction/connection
			// remains open.
			try {
				Thread.sleep(delayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new PaymentInterruptedException();
			}
			if (ThreadLocalRandom.current().nextInt(100) < failurePercentage) {
				payment.fail("PAYMENT_DECLINED");
				failed.increment();
			} else {
				payment.succeed();
				succeeded.increment();
			}
			return repository.save(payment);
		});
	}
}
