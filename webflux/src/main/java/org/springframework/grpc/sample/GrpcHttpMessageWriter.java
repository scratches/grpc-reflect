/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.grpc.sample;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;

public class GrpcHttpMessageWriter extends EncoderHttpMessageWriter<Message> {

	private static final String GRPC_STATUS_HEADER = "grpc-status";

	private static final String X_PROTOBUF_SCHEMA_HEADER = "X-Protobuf-Schema";

	private static final String X_PROTOBUF_MESSAGE_HEADER = "X-Protobuf-Message";

	private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap<>();

	/**
	 * Create a new {@code ProtobufHttpMessageWriter} with a default
	 * {@link ProtobufEncoder}.
	 */
	public GrpcHttpMessageWriter() {
		super(new GrpcEncoder());
	}

	/**
	 * Create a new {@code ProtobufHttpMessageWriter} with the given encoder.
	 * 
	 * @param encoder the Protobuf message encoder to use
	 */
	public GrpcHttpMessageWriter(Encoder<Message> encoder) {
		super(encoder);
	}

	@Override
	public Mono<Void> write(Publisher<? extends Message> inputStream, ResolvableType elementType,
			@Nullable MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints) {

		try {
			Message.Builder builder = getMessageBuilder(elementType.toClass());
			Descriptors.Descriptor descriptor = builder.getDescriptorForType();
			message.getHeaders().add(X_PROTOBUF_SCHEMA_HEADER, descriptor.getFile().getName());
			message.getHeaders().add(X_PROTOBUF_MESSAGE_HEADER, descriptor.getFullName());
			addTrailer(message);

			return super.write(inputStream, elementType, mediaType, message, hints);
		} catch (Exception ex) {
			return Mono.error(new EncodingException("Could not write Protobuf message: " + ex.getMessage(), ex));
		}
	}

	/**
	 * Create a new {@code Message.Builder} instance for the given class.
	 * <p>
	 * This method uses a ConcurrentHashMap for caching method lookups.
	 */
	private static Message.Builder getMessageBuilder(Class<?> clazz) throws Exception {
		Method method = methodCache.get(clazz);
		if (method == null) {
			method = clazz.getMethod("newBuilder");
			methodCache.put(clazz, method);
		}
		return (Message.Builder) method.invoke(clazz);
	}

	private void addTrailer(ReactiveHttpOutputMessage response) {
		response.getHeaders().add("Trailer", GRPC_STATUS_HEADER);
		while (response instanceof ServerHttpResponseDecorator) {
			response = ((ServerHttpResponseDecorator) response).getDelegate();
		}
		if (response instanceof AbstractServerHttpResponse server) {
			String grpcStatus = status(server.getStatusCode().value());
			HttpServerResponse httpServerResponse = (HttpServerResponse) ((AbstractServerHttpResponse) response)
					.getNativeResponse();
			httpServerResponse.trailerHeaders(h -> {
				h.set(GRPC_STATUS_HEADER, grpcStatus);
			});
		}
	}

	private String status(int status) {
		if (status >= 200 && status < 300) {
			return "0"; // OK
		} else if (status == 400) {
			return "3"; // INVALID_ARGUMENT
		} else if (status == 401) {
			return "16"; // UNAUTHENTICATED
		} else if (status == 403) {
			return "7"; // PERMISSION_DENIED
		} else if (status == 404) {
			return "5"; // NOT_FOUND
		} else if (status == 408) {
			return "4"; // DEADLINE_EXCEEDED
		} else if (status == 429) {
			return "8"; // RESOURCE_EXHAUSTED
		} else if (status == 501) {
			return "12"; // UNIMPLEMENTED
		} else if (status == 503) {
			return "14"; // UNAVAILABLE
		} else if (status >= 500 && status < 600) {
			return "13"; // INTERNAL
		}
		return "2"; // UNKNOWN
	}

}
