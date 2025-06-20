package org.springframework.grpc.sample;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.webflux.EmbeddedGrpcServer;
import org.springframework.grpc.webflux.GrpcDecoder;
import org.springframework.grpc.webflux.GrpcHttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.grpc.BindableService;

@SpringBootApplication
public class GrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

	@Bean
	@ConditionalOnBean(BindableService.class)
	public EmbeddedGrpcServer grpcServer(ObjectProvider<BindableService> bindableServices) throws IOException {
		EmbeddedGrpcServer server = new EmbeddedGrpcServer();
		for (BindableService service : bindableServices) {
			server.addService(service.bindService());
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

}

@Configuration
class WebConfiguration implements WebFluxConfigurer {

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
		configurer.customCodecs().register(new GrpcHttpMessageWriter());
		configurer.customCodecs().register(new GrpcDecoder());
	}
}
