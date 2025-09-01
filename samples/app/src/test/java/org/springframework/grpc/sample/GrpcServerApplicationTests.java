package org.springframework.grpc.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.grpc.reflect.DescriptorRegistrar;
import org.springframework.grpc.reflect.DynamicServiceFactory;
import org.springframework.grpc.reflect.DynamicStub;
import org.springframework.grpc.reflect.DynamicStubFactory;
import org.springframework.grpc.reflect.GrpcClient;
import org.springframework.grpc.reflect.GrpcMapping;
import org.springframework.grpc.sample.FooService.Input;
import org.springframework.grpc.sample.FooService.Output;
import org.springframework.test.annotation.DirtiesContext;

import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc.ServerReflectionStub;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" }, useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private Channel channel;

	@Autowired
	private FooClient fooClient;

	@Autowired
	private DescriptorRegistrar registry;

	@Test
	void contextLoads() {
	}

	@Test
	void reflectionServiceAllServices() {
		ServerReflectionStub reflectionService = ServerReflectionGrpc.newStub(this.channel);
		AtomicReference<Throwable> error = new AtomicReference<>();
		AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
		StreamObserver<ServerReflectionRequest> observer = reflectionService.serverReflectionInfo(
				new StreamObserver<ServerReflectionResponse>() {
					@Override
					public void onNext(ServerReflectionResponse value) {
						response.set(value);
					}

					@Override
					public void onError(Throwable t) {
						error.set(t);
					}

					@Override
					public void onCompleted() {
					}
				});
		observer.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
		Awaitility.await().until(() -> response.get() != null || error.get() != null);
		observer.onCompleted();
		assertThat(error.get()).isNull();
		assertThat(response.get()).isNotNull();
	}

	@Test
	void reflectionServiceService() {
		ServerReflectionStub reflectionService = ServerReflectionGrpc.newStub(this.channel);
		AtomicReference<Throwable> error = new AtomicReference<>();
		AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
		StreamObserver<ServerReflectionRequest> observer = reflectionService.serverReflectionInfo(
				new StreamObserver<ServerReflectionResponse>() {
					@Override
					public void onNext(ServerReflectionResponse value) {
						response.set(value);
					}

					@Override
					public void onError(Throwable t) {
						error.set(t);
					}

					@Override
					public void onCompleted() {
					}
				});
		observer.onNext(ServerReflectionRequest.newBuilder().setFileContainingSymbol("FooService").build());
		Awaitility.await().until(() -> response.get() != null || error.get() != null);
		observer.onCompleted();
		assertThat(error.get()).isNull();
		assertThat(response.get()).isNotNull();
	}

	@Test
	void dynamicServiceFromFunction() {
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = fooClient.ping(request);
		assertEquals("Alien", response.getName());
	}

	@Test
	void duplicateServiceFromFunction() {
		DynamicStub stub = new DynamicStub(registry, this.channel);
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.unary("EchoService/Echo", request, Hello.class);
		assertEquals("Alien", response.getName());
	}

	@Test
	void dynamicServiceFromInstance() {
		DynamicStub stub = new DynamicStub(registry, this.channel);
		Input request = new Input();
		Output response = stub.unary("FooService/Process", request, Output.class);
		assertThat(response).isNotNull();
	}

	@Test
	void dynamicServiceFromInstanceWithMapping() {
		DynamicStub stub = new DynamicStub(registry, this.channel);
		Hello request = new Hello("Foo");
		Hello response = stub.unary("FooService/Echo", request, Hello.class);
		assertThat(response).isNotNull();
	}

	@Test
	void pojoCall() {
		DynamicStub stub = new DynamicStub(registry, this.channel);
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.unary("Simple/SayHello", request, Hello.class);
		assertEquals("Hello ==> Alien", response.getName());
	}

	@TestConfiguration(proxyBeanMethods = false)
	@ImportGrpcClients(factory = DynamicStubFactory.class, types = FooClient.class)
	static class ExtraConfiguration {

		@Bean
		@Lazy
		Channel channel(GrpcChannelFactory channelFactory) {
			return channelFactory.createChannel("default");
		}

		@Bean
		BindableService echoService(DynamicServiceFactory factory) {
			return factory.service("EchoService").unary("Echo",
					Foo.class, Foo.class, Function.identity()).build();
		}

		@Bean
		DynamicStubFactory dynamicStubFactory(DescriptorRegistrar registry) {
			return new DynamicStubFactory(registry);
		}

	}

}

@GrpcClient(service = "FooService")
interface FooClient {

	@GrpcMapping(path = "Echo")
	Hello ping(Hello request);

	Output process(Input request);

}