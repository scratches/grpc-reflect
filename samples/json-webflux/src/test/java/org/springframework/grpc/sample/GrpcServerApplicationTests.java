package org.springframework.grpc.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webclient.test.autoconfigure.AutoConfigureWebClient;
import org.springframework.grpc.reflect.DescriptorCatalog;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.HelloWorldProto;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.WebClient;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.util.JsonFormat;

import io.grpc.MethodDescriptor.PrototypeMarshaller;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import tools.jackson.databind.ObjectMapper;
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
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(new Foo("Alien"))
				.retrieve()
				.bodyToFlux(Bar.class)
				.blockFirst();
		assertEquals("Hello ==> Alien", response.getMessage());
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