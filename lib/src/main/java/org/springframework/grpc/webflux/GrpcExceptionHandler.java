/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.grpc.webflux;

import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import io.grpc.Status.Code;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

/**
 * Exception handler for gRPC operations in Spring WebFlux applications.
 * <p>
 * This handler extends {@link GrpcCodecSupport} and implements
 * {@link WebExceptionHandler} and {@link Ordered} to provide centralized exception
 * handling for gRPC-related errors in reactive web applications, converting exceptions to
 * appropriate HTTP responses.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcExceptionHandler extends GrpcCodecSupport implements WebExceptionHandler, Ordered {

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		if (supportsMimeType(exchange.getRequest().getHeaders().getContentType())) {
			exchange.getResponse().getHeaders().setContentType(MediaType.parseMediaType("application/grpc"));
			addTrailer(exchange.getResponse());
			return exchange.getResponse().setComplete();
		}
		else {
			return Mono.error(ex); // Not a gRPC request, propagate the error
		}
	}

	private void addTrailer(ServerHttpResponse response) {
		response.getHeaders().add("Trailer", GRPC_STATUS_HEADER);
		while (response instanceof ServerHttpResponseDecorator) {
			response = ((ServerHttpResponseDecorator) response).getDelegate();
		}
		if (response instanceof AbstractServerHttpResponse server) {
			String grpcStatus = "" + Code.INTERNAL.ordinal(); // INTERNAL error code";
			HttpServerResponse httpServerResponse = (HttpServerResponse) (server).getNativeResponse();
			httpServerResponse.trailerHeaders(h -> {
				h.set(GRPC_STATUS_HEADER, grpcStatus);
			});
		}
	}

}
