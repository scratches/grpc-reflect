package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webclient.test.autoconfigure.AutoConfigureWebClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

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
		this.rest = builder.codecs(config -> {
			// Extra codecs are not needed, but they should be because the server should be
			// publishing NDJSON
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
	void parallelStreams() {
		Bar response = rest
				.post()
				.uri("Simple/ParallelHello")
				.contentType(MediaType.APPLICATION_NDJSON)
				.body(Flux.interval(Duration.ofSeconds(1))
						.take(10)
						.map(count -> new Foo("Alien(" + count + ")")), Foo.class)
				.retrieve()
				.bodyToFlux(Bar.class)
				.doOnNext(bar -> System.out.println("Received: " + bar.getMessage()))
				// Use blockLast() to see the arrival of the entire stream
				.blockFirst();
		assertEquals("Hello ==> Alien(0)", response.getMessage());
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