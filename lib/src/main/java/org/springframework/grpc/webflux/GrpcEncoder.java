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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Encoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.FastByteArrayOutputStream;
import org.springframework.util.MimeType;

import com.google.protobuf.Message;

import reactor.core.publisher.Flux;

public class GrpcEncoder extends GrpcCodecSupport implements Encoder<Message> {

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

		FastByteArrayOutputStream bos = new FastByteArrayOutputStream();
		try {
			bos.write(0);
			ByteBuffer buffer = ByteBuffer.allocate(4);
			buffer.order(ByteOrder.BIG_ENDIAN);
			buffer.putInt(message.getSerializedSize());
			bos.write(buffer.array());
			message.writeTo(bos);
			byte[] bytes = bos.toByteArrayUnsafe();
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
