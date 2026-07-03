package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webclient.test.autoconfigure.AutoConfigureWebClient;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.protobuf.util.JsonFormat;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "spring.grpc.server.enabled=false" })
@DirtiesContext
@AutoConfigureWebClient
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class).run();
	}

	private WebClient rest;

	@BeforeEach
	void setUp(@Autowired WebClient.Builder builder, @LocalServerPort int port) {
		this.rest = builder.baseUrl("http://localhost:" + port).build();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void serverResponds() {
		Bar response = rest
				.post()
				.uri("Simple/SayHello")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new Foo("Alien"))
				.retrieve()
				.bodyToMono(Bar.class)
				.block();
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@Test
	void serverStreams() {
		String response = rest
				.post()
				.uri("Simple/StreamHello")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new Foo("Alien"))
				.retrieve()
				.bodyToFlux(String.class)
				.blockFirst();
		Bar bar = new ObjectMapper().readValue(response, Bar.class);
		assertEquals("Hello (0) ==> Alien", bar.getMessage());
	}

	@Test
	void jsonEncoding() throws Exception {
		String response = JsonFormat.printer().omittingInsignificantWhitespace()
				.print(HelloReply.newBuilder().setMessage("Hello ==> Alien & Martian").build());
		Bar bar = new ObjectMapper().readValue(response, Bar.class);
		assertEquals("Hello ==> Alien & Martian", bar.getMessage());
		// assertEquals("{\"message\":\"Hello \u003d\u003d\u003e Alien \u0026 Martian\"}", response);
	}

	static class Foo {
		private String name;

		public Foo(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	static class Bar {
		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String name) {
			this.message = name;
		}
	}

}