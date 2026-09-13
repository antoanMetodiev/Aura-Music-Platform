package com.aura.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlation id for every request that passes through the gateway (Project-Info.md §10: "request
 * correlation, request IDs"). Runs first, before routing: mints an {@code X-Request-Id} when the
 * caller didn't send one, forwards it to whichever service handles the request, and echoes it back
 * on the response so a client (or us, reading logs) can tie a browser request to backend logs.
 * Downstream services (see catalog-svc's {@code RequestIdFilter}) reuse this value instead of
 * minting their own, so the id stays stable end to end.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER, requestId)
                .build();

        // The routed service (see catalog-svc's RequestIdFilter) echoes this same id back on its
        // own response, which the gateway forwards as-is — adding it again here would just
        // duplicate the header. Only set it ourselves when nothing came back (route failed to
        // match, or the gateway answers directly without proxying).
        String finalRequestId = requestId;
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    if (!exchange.getResponse().getHeaders().containsHeader(HEADER)) {
                        exchange.getResponse().getHeaders().add(HEADER, finalRequestId);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
