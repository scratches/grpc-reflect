package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.grpc.reflect.DynamicServiceFactory;
import org.springframework.grpc.reflect.Foo;
import org.springframework.test.annotation.DirtiesContext;

import com.example.hello.HelloReply;
import com.example.hello.HelloRequest;
import com.example.hello.SimpleGrpc;
import com.example.hello.SimpleGrpc.SimpleBlockingStub;
import com.example.hello.SimpleGrpc.SimpleStub;

import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;

@SpringBootTest(
		properties = { "spring.grpc.server.port=0",
				"spring.grpc.client.channel.default.target=0.0.0.0:${local.grpc.server.port}" },
		useMainMethod = UseMainMethod.ALWAYS)
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
		SimpleBlockingStub stub = SimpleGrpc.newBlockingStub(this.channelFactory.createChannel("default"));
		HelloRequest.Builder request = HelloRequest.newBuilder();
		request.setName("Alien");
		HelloReply response = stub.sayHello(request.build());
		assertEquals("Alien", response.getMessage());
	}

	@Test
	void streamingServiceFromFunction() {
		SimpleBlockingStub stub = SimpleGrpc.newBlockingStub(this.channelFactory.createChannel("default"));
		HelloRequest.Builder request = HelloRequest.newBuilder();
		request.setName("Alien");
		HelloReply response = stub.streamHello(request.build()).next();
		assertEquals("Alien (0)", response.getMessage());
	}

	@Test
	void bidiServiceFromFunction() {
		SimpleStub stub = SimpleGrpc.newStub(this.channelFactory.createChannel("default"));
		HelloRequest.Builder request = HelloRequest.newBuilder();
		request.setName("Alien");
		AtomicBoolean done = new AtomicBoolean();
		StreamObserver<HelloRequest> requests = stub.parallelHello(new StreamObserver<HelloReply>() {
			@Override
			public void onNext(HelloReply value) {
				assertEquals("Alien", value.getMessage());
			}

			@Override
			public void onCompleted() {
				done.set(true);
			}

			@Override
			public void onError(Throwable t) {
			}
		});
		requests.onNext(request.build());
		requests.onCompleted();
		Awaitility.await().until(() -> done.get());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExtraConfiguration {

		@Bean
		BindableService echoService(DynamicServiceFactory factory) {
			return factory.service("Simple")
				.unary("SayHello", Foo.class, Foo.class, Function.identity())
				.stream("StreamHello", Foo.class, Foo.class,
						foo -> Flux.interval(Duration.ofMillis(200))
							.take(5)
							.map(val -> new Foo(foo.getName() + " (" + val + ")")))
				.bidi("ParallelHello", Foo.class, Foo.class, foos -> Flux.from(foos).map(foo -> new Foo(foo.getName())))
				.build();
		}

	}

}
