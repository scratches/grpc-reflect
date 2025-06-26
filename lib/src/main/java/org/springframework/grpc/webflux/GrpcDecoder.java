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
package org.springframework.grpc.webflux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.MimeType;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GrpcDecoder extends GrpcCodecSupport implements Decoder<Message> {

	/** The default max size for aggregating messages. */
	protected static final int DEFAULT_MESSAGE_MAX_SIZE = 256 * 1024;

	private static final ConcurrentMap<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap<>();

	private final ExtensionRegistry extensionRegistry;

	private int maxMessageSize = DEFAULT_MESSAGE_MAX_SIZE;

	/**
	 * Construct a new {@code ProtobufDecoder}.
	 */
	public GrpcDecoder() {
		this(ExtensionRegistry.newInstance());
	}

	/**
	 * Construct a new {@code ProtobufDecoder} with an initializer that allows the
	 * registration of message extensions.
	 * 
	 * @param extensionRegistry a message extension registry
	 */
	public GrpcDecoder(ExtensionRegistry extensionRegistry) {
		Assert.notNull(extensionRegistry, "ExtensionRegistry must not be null");
		this.extensionRegistry = extensionRegistry;
	}

	/**
	 * The max size allowed per message.
	 * <p>
	 * By default, this is set to 256K.
	 * 
	 * @param maxMessageSize the max size per message, or -1 for unlimited
	 */
	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	/**
	 * Return the {@link #setMaxMessageSize configured} message size limit.
	 * 
	 * @since 5.1.11
	 */
	public int getMaxMessageSize() {
		return this.maxMessageSize;
	}

	@Override
	public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
		return Message.class.isAssignableFrom(elementType.toClass()) && supportsMimeType(mimeType);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Flux<Message> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		MessageDecoderFunction decoderFunction = new MessageDecoderFunction(elementType, this.maxMessageSize);

		return (Flux<Message>) Flux.from(inputStream)
				.flatMapIterable(decoderFunction)
				.doOnTerminate(decoderFunction::discard);
	}

	@Override
	public Mono<Message> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {

		return DataBufferUtils.join(inputStream, this.maxMessageSize)
				.map(dataBuffer -> decode(dataBuffer, elementType, mimeType, hints));
	}

	@Override
	public Message decode(DataBuffer dataBuffer, ResolvableType targetType,
			@Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {

		try {
			Message.Builder builder = getMessageBuilder(targetType.toClass());
			ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
			byteBuffer.order(ByteOrder.BIG_ENDIAN);
			dataBuffer.toByteBuffer(byteBuffer);
			byteBuffer.get(); // Skip the first byte (gRPC header)
			byteBuffer.getInt(); // Read the message size (4 bytes)
			builder.mergeFrom(CodedInputStream.newInstance(byteBuffer), this.extensionRegistry);
			return builder.build();
		} catch (IOException ex) {
			throw new DecodingException("I/O error while parsing input stream", ex);
		} catch (Exception ex) {
			throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
		} finally {
			DataBufferUtils.release(dataBuffer);
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

	@Override
	public List<MimeType> getDecodableMimeTypes() {
		return getMimeTypes();
	}

	private class MessageDecoderFunction implements Function<DataBuffer, Iterable<? extends Message>> {

		private final ResolvableType elementType;

		private final int maxMessageSize;

		@Nullable
		private DataBuffer output;

		private int messageBytesToRead;

		public MessageDecoderFunction(ResolvableType elementType, int maxMessageSize) {
			this.elementType = elementType;
			this.maxMessageSize = maxMessageSize;
		}

		@Override
		public Iterable<? extends Message> apply(DataBuffer input) {
			try {
				List<Message> messages = new ArrayList<>();
				int remainingBytesToRead;
				int chunkBytesToRead;

				do {
					if (this.output == null) {
						if (!readMessageSize(input)) {
							return messages;
						}
						if (this.maxMessageSize > 0 && this.messageBytesToRead > this.maxMessageSize) {
							throw new DataBufferLimitException(
									"The number of bytes to read for message " +
											"(" + this.messageBytesToRead + ") exceeds " +
											"the configured limit (" + this.maxMessageSize + ")");
						}
						this.output = input.factory().allocateBuffer(this.messageBytesToRead);
					}

					chunkBytesToRead = Math.min(this.messageBytesToRead, input.readableByteCount());
					remainingBytesToRead = input.readableByteCount() - chunkBytesToRead;

					byte[] bytesToWrite = new byte[chunkBytesToRead];
					input.read(bytesToWrite, 0, chunkBytesToRead);
					this.output.write(bytesToWrite);
					this.messageBytesToRead -= chunkBytesToRead;

					if (this.messageBytesToRead == 0) {
						if (chunkBytesToRead > 0) {
							ByteBuffer byteBuffer = ByteBuffer.allocate(this.output.readableByteCount());
							this.output.toByteBuffer(byteBuffer);
							CodedInputStream stream = CodedInputStream.newInstance(byteBuffer);
							DataBufferUtils.release(this.output);
							Message message = getMessageBuilder(this.elementType.toClass())
									.mergeFrom(stream, extensionRegistry)
									.build();
							messages.add(message);
						}
						this.output = null;
					}
				} while (remainingBytesToRead > 0);
				return messages;
			} catch (DecodingException ex) {
				throw ex;
			} catch (IOException ex) {
				throw new DecodingException("I/O error while parsing input stream", ex);
			} catch (Exception ex) {
				throw new DecodingException("Could not read Protobuf message: " + ex.getMessage(), ex);
			} finally {
				DataBufferUtils.release(input);
			}
		}

		/**
		 * Parse message size from gRPC header.
		 */
		private boolean readMessageSize(DataBuffer input) {
			if (input.readableByteCount() < 5) {
				return false;
			}
			byte[] header = new byte[5];
			input.read(header);
			ByteBuffer byteBuffer = ByteBuffer.wrap(header);
			byteBuffer.order(ByteOrder.BIG_ENDIAN);
			byteBuffer.get(); // Skip the first byte (gRPC header)
			this.messageBytesToRead = byteBuffer.getInt(); // Read the message size (4 bytes)
			return true;
		}

		public void discard() {
			if (this.output != null) {
				DataBufferUtils.release(this.output);
			}
		}
	}

}
