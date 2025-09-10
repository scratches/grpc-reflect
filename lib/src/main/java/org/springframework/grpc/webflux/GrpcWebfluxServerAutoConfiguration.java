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

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.grpc.BindableService;

@Configuration
@ConditionalOnBean(BindableService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
/**
 * Auto-configuration for gRPC server functionality in Spring WebFlux.
 * <p>
 * This configuration class provides automatic setup of server-side gRPC
 * components for Spring WebFlux applications, including server beans,
 * handlers, and other infrastructure required for serving gRPC requests.
 * 
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcWebfluxServerAutoConfiguration {

	@Bean
	public EmbeddedGrpcServer grpcServer(ObjectProvider<BindableService> bindableServices) throws IOException {
		EmbeddedGrpcServer server = new EmbeddedGrpcServer();
		for (BindableService service : bindableServices) {
			server.addService(service);
		}
		server.start();
		return server;
	}

	@Bean
	@ConditionalOnBean(EmbeddedGrpcServer.class)
	public RouterFunction<ServerResponse> grpcRoutes(EmbeddedGrpcServer server) {
		RouterFunctions.Builder builder = RouterFunctions.route();
		for (String path : server.getHandlers().keySet()) {
			builder.POST(path, server.getHandlers().get(path));
		}
		return builder.build();
	}

	@Configuration
	static class WebConfiguration implements WebFluxConfigurer {

		@Override
		public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
			configurer.customCodecs().register(new GrpcHttpMessageWriter());
			configurer.customCodecs().register(new GrpcDecoder());
		}

		@Bean
		public GrpcExceptionHandler grpcExceptionHandler() {
			return new GrpcExceptionHandler();
		}

	}

}
