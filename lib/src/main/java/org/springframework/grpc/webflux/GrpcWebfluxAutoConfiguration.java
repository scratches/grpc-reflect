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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import io.grpc.BindableService;

@Configuration
@ConditionalOnMissingBean(BindableService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
/**
 * Auto-configuration for gRPC integration with Spring WebFlux.
 * <p>
 * This configuration class implements {@link WebFluxConfigurer} to automatically
 * configure the necessary components for gRPC support in Spring WebFlux
 * applications, including codecs, handlers, and other integration components.
 * 
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcWebfluxAutoConfiguration implements WebFluxConfigurer {

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
