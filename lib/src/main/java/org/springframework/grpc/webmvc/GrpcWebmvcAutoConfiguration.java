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
package org.springframework.grpc.webmvc;

import java.util.List;

import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http2.Http2Protocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.grpc.BindableService;

@Configuration
@ConditionalOnMissingBean(BindableService.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GrpcWebmvcAutoConfiguration implements WebMvcConfigurer {

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		GrpcHttpMessageConverter converter = new GrpcHttpMessageConverter();
		converters.add(converter);
	}

	@Bean
	public GrpcExceptionHandler grpcExceptionHandler() {
		return new GrpcExceptionHandler();
	}

	@Bean
	@ConditionalOnClass(UpgradeProtocol.class)
	public TomcatConnectorCustomizer customizer() {
		return (connector) -> {
			for (UpgradeProtocol protocol : connector.findUpgradeProtocols()) {
				if (protocol instanceof Http2Protocol http2Protocol) {
					http2Protocol.setOverheadWindowUpdateThreshold(0);
				}
			}
		};
	}

}
