package com.example.flashsale.product;

import static com.example.flashsale.events.FlashSaleEvents.*;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
class ProductKafkaHandlers {
	private final ProductService service;
	private final KafkaTemplate<String, Object> kafka;

	ProductKafkaHandlers(ProductService service, KafkaTemplate<String, Object> kafka) {
		this.service = service;
		this.kafka = kafka;
	}

	@KafkaListener(topics = INVENTORY_REDUCTION_REQUESTED)
	void reduce(InventoryReductionRequested event) {
		MDC.put("orderId", event.orderId());
		MDC.put("requestId", event.requestId());
		try {
			Product product = service.reduce(event.productId(), event.quantity());
			kafka.send(INVENTORY_REDUCTION_COMPLETED, event.orderId(),
					new InventoryReductionCompleted(event.orderId(), event.requestId(), event.userId(), event.productId(),
							event.quantity(), true, product.getPrice(), null));
		} catch (RuntimeException e) {
			kafka.send(INVENTORY_REDUCTION_COMPLETED, event.orderId(),
					new InventoryReductionCompleted(event.orderId(), event.requestId(), event.userId(), event.productId(),
							event.quantity(), false, null, e.getMessage()));
		} finally {
			MDC.clear();
		}
	}

	@KafkaListener(topics = INVENTORY_RESTORATION_REQUESTED)
	void restore(InventoryRestorationRequested event) {
		MDC.put("orderId", event.orderId());
		MDC.put("requestId", event.requestId());
		try {
			service.restore(event.productId(), event.quantity());
		} finally {
			MDC.clear();
		}
	}
}
