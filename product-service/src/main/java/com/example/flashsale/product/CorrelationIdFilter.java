package com.example.flashsale.product;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
	private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
	static final String HEADER = "X-Correlation-ID";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = request.getHeader(HEADER);
		if (id == null || id.isBlank())
			id = UUID.randomUUID().toString();
		MDC.put("correlationId", id);
		MDC.put("requestId", id);
		response.setHeader(HEADER, id);
		long started = System.nanoTime();
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.put("processingDurationMs", String.valueOf((System.nanoTime() - started) / 1_000_000));
			MDC.put("finalStatus", String.valueOf(response.getStatus()));
//			log.info("Request completed method={} path={}", request.getMethod(), request.getRequestURI());
			MDC.clear();
		}
	}
}
