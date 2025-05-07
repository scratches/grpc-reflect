package org.springframework.grpc.sample;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.reflect.DescriptorRegistry;
import org.springframework.grpc.reflect.DynamicServiceFactory;
import org.springframework.grpc.reflect.DynamicStub;
import org.springframework.grpc.reflect.EnableGrpcMapping;
import org.springframework.grpc.sample.FooService.Input;
import org.springframework.grpc.sample.FooService.Output;
import org.springframework.test.annotation.DirtiesContext;

import io.grpc.BindableService;
import io.grpc.Channel;

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" }, useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private Channel channel;

	@Test
	void contextLoads() {
	}

	@Test
	void dynamicServiceFromFunction() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.channel);
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.call("FooService/Echo", request, Hello.class);
		assertEquals("Alien", response.getName());
	}

	@Test
	void dynamicServiceFromInstance() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.channel);
		Input request = new Input();
		Output response = stub.call("FooService/Process", request, Output.class);
		assertThat(response).isNotNull();
	}

	@Test
	void pojoCall() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.channel);
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.call("Simple/SayHello", request, Hello.class);
		assertEquals("Hello ==> Alien", response.getName());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExtraConfiguration {

		@Bean
		@Lazy
		Channel channel(GrpcChannelFactory channelFactory) {
			return channelFactory.createChannel("default");
		}

		// TODO: support re-using types in different services
		// @Bean
		// BindableService echoService(DynamicServiceFactory factory) {
		// 	return factory.service("EchoService").method("Echo",
		// 			Foo.class, Foo.class, Function.identity()).build();
		// }

	}

}

