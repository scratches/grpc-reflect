package org.springframework.grpc.sample;

import java.util.List;

import org.apache.coyote.UpgradeProtocol;
import org.apache.coyote.http2.Http2Protocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.webmvc.GrpcExceptionHandler;
import org.springframework.grpc.webmvc.GrpcHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class GrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

}

@Configuration
class WebConfiguration implements WebMvcConfigurer {

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
