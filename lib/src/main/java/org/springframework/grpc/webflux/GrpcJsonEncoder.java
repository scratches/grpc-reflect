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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.util.MimeType;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

import reactor.core.publisher.Flux;

/**
 * WebFlux encoder for gRPC protocol buffer messages.
 * <p>
 * This encoder extends {@link GrpcCodecSupport} and implements {@link Encoder}
 * to provide
 * encoding capabilities for protocol buffer {@link Message} objects in Spring
 * WebFlux
 * reactive streams, converting messages to byte streams.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcJsonEncoder extends GrpcCodecSupport implements HttpMessageEncoder<Message> {

	private static final MimeType[] MIME_TYPES = new MimeType[] {
		MediaType.APPLICATION_JSON,
		new MediaType("application", "*+json"),
		MediaType.APPLICATION_NDJSON,
			new MediaType("application", "*+ndjson"),
		};

	private static final byte[] NEWLINE_SEPARATOR = { '\n' };

	private static final byte[] EMPTY_SEPARATOR = new byte[0];

	private final List<MediaType> streamingMediaTypes = new ArrayList<>(1);

	public GrpcJsonEncoder() {
		super(MIME_TYPES);
		setStreamingMediaTypes(List.of(MediaType.APPLICATION_NDJSON));
	}

	public GrpcJsonEncoder(MimeType[] mimeTypes) {
		super(mimeTypes);
	}

	/**
	 * Configure "streaming" media types for which flushing should be performed
	 * automatically vs at the end of the stream.
	 */
	public void setStreamingMediaTypes(List<MediaType> mediaTypes) {
		this.streamingMediaTypes.clear();
		this.streamingMediaTypes.addAll(mediaTypes);
	}

	@Override
	public List<MediaType> getStreamingMediaTypes() {
		return this.streamingMediaTypes;
	}

	@Override
	public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return Message.class.isAssignableFrom(elementType.toClass()) && supportsMimeType(mimeType);
	}

	@Override
	public Flux<DataBuffer> encode(Publisher<? extends Message> inputStream, DataBufferFactory bufferFactory,
			ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return Flux.from(inputStream).map(message -> encodeValue(message, bufferFactory, mimeType));
	}

	@Override
	public DataBuffer encodeValue(Message message, DataBufferFactory bufferFactory, ResolvableType valueType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return encodeValue(message, bufferFactory, mimeType);
	}

	private DataBuffer encodeValue(Message message, DataBufferFactory bufferFactory, @Nullable MimeType mimeType) {

		try {
			JsonFormat.Printer printer = JsonFormat.printer().omittingInsignificantWhitespace();
			byte[] bytes = printer.print(message).getBytes(StandardCharsets.UTF_8);
			byte[] separator = getStreamingMediaTypeSeparator(mimeType);
			byte[] record = new byte[bytes.length + separator.length];
			System.arraycopy(bytes, 0, record, 0, bytes.length);
			System.arraycopy(separator, 0, record, bytes.length, separator.length); // Record separator for NDJSON
			return bufferFactory.wrap(record);
		} catch (IOException ex) {
			throw new IllegalStateException("Unexpected I/O error while writing to data buffer", ex);
		}
	}

	protected byte @Nullable [] getStreamingMediaTypeSeparator(@Nullable MimeType mimeType) {
		for (MediaType streamingMediaType : this.streamingMediaTypes) {
			if (streamingMediaType.isCompatibleWith(mimeType)) {
				return NEWLINE_SEPARATOR;
			}
		}
		return EMPTY_SEPARATOR;
	}

	@Override
	public List<MimeType> getEncodableMimeTypes() {
		return getMimeTypes();
	}

}
