package org.springframework.grpc.sample;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

}

@Configuration
class WebConfiguration implements WebFluxConfigurer {

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		configurer.customCodecs().register(new GrpcEncoder());
		configurer.customCodecs().register(new GrpcDecoder());
	}
}

@Component
class ContentTypeWebFilter implements WebFilter {

	private static final String GRPC_STATUS_HEADER = "grpc-status";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		MediaType type = exchange.getRequest().getHeaders().getContentType();
		ServerWebExchange active = exchange;
		boolean isGrpc = type != null && MediaType.valueOf("application/grpc").isCompatibleWith(type);
		return chain.filter(active).doOnSubscribe((item) -> {
			if (isGrpc) {
				addTrailer(active.getResponse());
			}
		});
	}

	private void addTrailer(ServerHttpResponse response) {
		response.getHeaders().add("Trailer", GRPC_STATUS_HEADER);
		while (response instanceof ServerHttpResponseDecorator) {
			response = ((ServerHttpResponseDecorator) response).getDelegate();
		}
		if (response instanceof AbstractServerHttpResponse) {
			String grpcStatus = status(response.getStatusCode().value());
			HttpServletResponse servletResponse = (HttpServletResponse) ((AbstractServerHttpResponse) response)
					.getNativeResponse();
			servletResponse.setTrailerFields(() -> {
				return Map.of(GRPC_STATUS_HEADER, grpcStatus);
			});
			// HttpServerResponse httpServerResponse = (HttpServerResponse)
			// ((AbstractServerHttpResponse) response)
			// .getNativeResponse();
			// httpServerResponse.trailerHeaders(h -> {
			// h.set(GRPC_STATUS_HEADER, grpcStatus);
			// });
		}
	}

	private String status(int status) {
		if (status >= 200 && status < 300) {
			return "0"; // OK
		} else if (status == 400) {
			return "3"; // INVALID_ARGUMENT
		} else if (status == 401) {
			return "16"; // UNAUTHENTICATED
		} else if (status == 403) {
			return "7"; // PERMISSION_DENIED
		} else if (status == 404) {
			return "5"; // NOT_FOUND
		} else if (status == 408) {
			return "4"; // DEADLINE_EXCEEDED
		} else if (status == 429) {
			return "8"; // RESOURCE_EXHAUSTED
		} else if (status == 501) {
			return "12"; // UNIMPLEMENTED
		} else if (status == 503) {
			return "14"; // UNAVAILABLE
		} else if (status >= 500 && status < 600) {
			return "13"; // INTERNAL
		}
		return "2"; // UNKNOWN
	}
}