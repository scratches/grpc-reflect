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
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.util.JsonFormat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "spring.grpc.server.enabled=false", "debug=true" })
@DirtiesContext
@AutoConfigureWebClient
public class GrpcServerApplicationTests {

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class).run();
	}

	private WebClient rest;

	@BeforeEach
	void setUp(@Autowired WebClient.Builder builder, @LocalServerPort int port) {
		this.rest = builder.codecs(config -> {
			MimeType[] mimeTypes = new MimeType[] {
					MediaType.APPLICATION_JSON,
					new MediaType("application", "*+json"),
					MediaType.APPLICATION_NDJSON,
					MediaType.valueOf("application/*+x-ndjson")
			};
			JacksonJsonDecoder decoder = new JacksonJsonDecoder(JsonMapper.builder(), mimeTypes);
			config.defaultCodecs().jacksonJsonDecoder(decoder);
		}).baseUrl("http://localhost:" + port).build();
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
		Bar response = rest
				.post()
				.uri("Simple/StreamHello")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new Foo("Alien"))
				.retrieve()
				.bodyToFlux(Bar.class)
				.blockFirst();
		assertEquals("Hello(0) ==> Alien", response.getMessage());
	}

	@Test
	void jsonEncoding() throws Exception {
		String response = JsonFormat.printer().omittingInsignificantWhitespace()
				.print(HelloReply.newBuilder().setMessage("Hello ==> Alien & Martian").build());
		Bar bar = new ObjectMapper().readValue(response, Bar.class);
		assertEquals("Hello ==> Alien & Martian", bar.getMessage());
		assertEquals("{\"message\":\"Hello \\u003d\\u003d\\u003e Alien \\u0026 Martian\"}", response);
	}

	@Test
	void gsonEncoding() throws Exception {
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		// Gson escapes HTML by default, which is why the Protobuf JSON encoding is
		// different.
		String response = gson.toJson(new Bar("Hello ==> Alien & Martian"));
		Bar bar = new ObjectMapper().readValue(response, Bar.class);
		assertEquals("Hello ==> Alien & Martian", bar.getMessage());
		assertEquals("{\"message\":\"Hello ==> Alien & Martian\"}", response);
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

		public Bar() {
		}

		public Bar(String message) {
			this.message = message;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String name) {
			this.message = name;
		}
	}

}