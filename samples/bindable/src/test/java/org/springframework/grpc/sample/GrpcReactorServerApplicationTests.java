package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.ReactorHelloGrpc.ReactorHelloStub;
import org.springframework.test.annotation.DirtiesContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.server.port}" })
@DirtiesContext
public class GrpcReactorServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class).run();
	}

	@Autowired
	private ReactorHelloStub stub;

	@Test
	void contextLoads() {
	}

	@Test
	void serverResponds() {
		HelloReply response = stub.sayHello(Mono.just(HelloRequest.newBuilder().setName("Alien").build())).block();
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@Test
	void serverStreams() {
		HelloReply response = stub.streamHello(HelloRequest.newBuilder().setName("Alien").build()).blockFirst();
		assertEquals("Hello(0) ==> Alien", response.getMessage());
	}

	@Test
	void serverParallels() {
		HelloReply response = stub.parallelHello(Flux.just(HelloRequest.newBuilder().setName("Alien").build())).blockFirst();
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@TestConfiguration
	@ImportGrpcClients(types =  ReactorHelloStub.class)
	static class ExtraConfiguration {
	}

}