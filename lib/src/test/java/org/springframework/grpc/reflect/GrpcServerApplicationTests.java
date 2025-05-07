package org.springframework.grpc.reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" }, useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private GrpcChannelFactory channelFactory;

	@Test
	void contextLoads() {
	}

	@Test
	void dynamicServiceFromFunction() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.channelFactory.createChannel("default"));
		Foo request = new Foo();
		request.setName("Alien");
		Foo response = stub.call("EchoService/Echo", request, Foo.class);
		assertEquals("Alien", response.getName());
	}

	@Test
	void dynamicServiceFromInstance() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.channelFactory.createChannel("default"));
		Input request = new Input();
		Output response = stub.call("FooService/Process", request, Output.class);
		assertThat(response).isNotNull();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableGrpcMapping
	static class ExtraConfiguration {

		@Bean
		BindableService echoService(DynamicServiceFactory factory) {
			return factory.service("EchoService").method("Echo",
					Foo.class, Foo.class, Function.identity()).build();
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
