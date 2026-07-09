package org.springframework.grpc.webflux;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.MimeType;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import reactor.core.publisher.Flux;

/**
 * WebFlux encoder for gRPC protocol buffer messages.
 * <p>
 * This encoder extends {@link GrpcCodecSupport} and implements {@link Encoder} to provide
 * encoding capabilities for protocol buffer {@link Message} objects in Spring WebFlux
 * reactive streams, converting messages to byte streams.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcJsonEncoder extends GrpcCodecSupport implements Encoder<Message> {

	final static MimeType[] MIME_TYPES = new MimeType[] { new MimeType("application", "json") };

	public GrpcJsonEncoder() {
		super(MIME_TYPES);
	}

	public GrpcJsonEncoder(MimeType[] mimeTypes) {
		super(mimeTypes);
	}

	@Override
	public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return Message.class.isAssignableFrom(elementType.toClass()) && supportsMimeType(mimeType);
	}

	@Override
	public Flux<DataBuffer> encode(Publisher<? extends Message> inputStream, DataBufferFactory bufferFactory,
			ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return Flux.from(inputStream).map(message -> encodeValue(message, bufferFactory));
	}

	@Override
	public DataBuffer encodeValue(Message message, DataBufferFactory bufferFactory, ResolvableType valueType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return encodeValue(message, bufferFactory);
	}

	private DataBuffer encodeValue(Message message, DataBufferFactory bufferFactory) {

		try {
			JsonFormat.Printer printer = JsonFormat.printer().omittingInsignificantWhitespace();
			byte[] bytes = printer.print(message).getBytes(StandardCharsets.UTF_8);
			return bufferFactory.wrap(bytes);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unexpected I/O error while writing to data buffer", ex);
		}
	}

	@Override
	public List<MimeType> getEncodableMimeTypes() {
		return getMimeTypes();
	}

}
