package org.springframework.grpc.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.test.annotation.DirtiesContext;

import io.grpc.BindableService;

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" }, useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationTests {

	private static Log log = LogFactory.getLog(GrpcServerApplicationTests.class);

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private SimpleGrpc.SimpleBlockingStub stub;

	@Test
	void contextLoads() {
	}

	@Test
	void serverResponds() {
		log.info("Testing");
		HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@Test
	void dynamicServiceFromFunction() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.stub.getChannel());
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.call("EchoService/Echo", request, Hello.class);
		assertEquals("Alien", response.getName());
	}

	@Test
	void dynamicServiceFromInstance() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.stub.getChannel());
		Input request = new Input();
		Output response = stub.call("FooService/Process", request, Output.class);
		assertThat(response).isNotNull();
	}

	@Test
	void pojoCall() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.stub.getChannel());
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.call("Simple/SayHello", request, Hello.class);
		assertEquals("Hello ==> Alien", response.getName());
	}

	static class Hello {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExtraConfiguration {

		@Bean
		DynamicServiceFactory factory () {
			return new DynamicServiceFactory(new DescriptorRegistry());
		}

		@Bean
		BindableService echoService(DynamicServiceFactory factory) {
			// TODO: support adding Echo to FooService (can't duplicate another bindable service)
			return factory.service("EchoService/Echo",
				Foo.class, Foo.class, Function.identity());
		}

		@Bean
		BindableService fooService(DynamicServiceFactory factory) {
			return factory.service(new FooService(), "process");
		}

	}

	static class Input {}
	static class Output {}

	static class FooService {
		public Output process(Input input) {
			return new Output();
		}
	}

}
