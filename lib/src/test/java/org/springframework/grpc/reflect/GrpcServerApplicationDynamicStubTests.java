package org.springframework.grpc.reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.reflect.FooService.Input;
import org.springframework.grpc.reflect.FooService.Output;
import org.springframework.test.annotation.DirtiesContext;

import io.grpc.BindableService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(
		properties = { "spring.grpc.server.port=0",
				"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" },
		useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationDynamicStubTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private GrpcChannelFactory channelFactory;

	@Autowired
	private DescriptorRegistry registry;

	@Test
	void contextLoads() {
	}

	@Test
	void dynamicServiceFromFunction() {
		DynamicStub stub = new DynamicStub(registry, this.channelFactory.createChannel("default"));
		Foo request = new Foo();
		request.setName("Alien");
		Foo response = stub.unary("EchoService/Echo", request, Foo.class);
		assertEquals("Alien", response.getName());
	}

	@Test
	void dynamicStreamFromFunction() {
		// It doesn't have to be the registry from the application context
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.unary("EchoService/Stream", Foo.class, Foo.class);
		DynamicStub stub = new DynamicStub(registry, this.channelFactory.createChannel("default"));
		Foo request = new Foo();
		request.setName("Alien");
		Flux<Foo> response = stub.stream("EchoService/Stream", request, Foo.class);
		assertEquals("Alien (0)", response.blockFirst().getName());
	}

	@Test
	void dynamicBidiFromFunction() {
		DynamicStub stub = new DynamicStub(this.registry, this.channelFactory.createChannel("default"));
		Foo request = new Foo();
		request.setName("Alien");
		Flux<Foo> response = stub.bidi("EchoService/Parallel", Mono.just(request), Foo.class, Foo.class);
		assertEquals("Alien", response.blockFirst().getName());
	}

	@Test
	void dynamicServiceFromInstance() {
		DynamicStub stub = new DynamicStub(this.registry, this.channelFactory.createChannel("default"));
		Input request = new Input();
		Output response = stub.unary("FooService/Process", request, Output.class);
		assertThat(response).isNotNull();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableGrpcMapping
	static class ExtraConfiguration {

		@Bean
		BindableService echoService(DynamicServiceFactory factory) {
			return factory.service("EchoService")
				.unary("Echo", Foo.class, Foo.class, Function.identity())
				.stream("Stream", Foo.class, Foo.class,
						foo -> Flux.interval(Duration.ofMillis(200))
							.take(5)
							.map(value -> new Foo(foo.getName() + " (" + value + ")")))
				.bidi("Parallel", Foo.class, Foo.class, foos -> Flux.from(foos).map(foo -> new Foo(foo.getName())))
				.build();
		}

	}

}

@GrpcController
class FooService {

	@GrpcMapping
	public Foo echo(Foo input) {
		return input;
	}

	@GrpcMapping
	public Output process(Input input) {
		return new Output();
	}

	static class Input {

	}

	static class Output {

	}

}
