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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.util.MimeType;

public class GrpcDecoderTests {

	@Test
	void testCanDecode() {
		GrpcDecoder decoder = new GrpcDecoder();
		assertThat(decoder.canDecode(ResolvableType.forClass(HelloReply.class), MimeType.valueOf("application/grpc")))
				.isTrue();
	}

	@Test
	void testDecode() {
		GrpcDecoder decoder = new GrpcDecoder();
		HelloReply message = HelloReply.newBuilder()
				.setMessage("Hello World")
				.build();
		int capacity = message.getSerializedSize() + 5;
		DataBuffer data = DefaultDataBufferFactory.sharedInstance.allocateBuffer(capacity);
		ByteBuffer buffer = ByteBuffer.allocate(capacity);
		buffer.order(ByteOrder.BIG_ENDIAN);
		buffer.put((byte) 0); // gRPC header
		buffer.putInt(message.getSerializedSize());
		buffer.put(message.toByteArray());
		buffer.position(0);
		buffer.limit(message.getSerializedSize() + 5); // 5 for header
		data.write(buffer);
		HelloReply reply = (HelloReply) decoder.decode(data, ResolvableType.forClass(HelloReply.class),
				MimeType.valueOf("application/grpc"), null);
		assertThat(reply).isNotNull();
		assertThat(reply.getMessage()).isEqualTo("Hello World");
	}
}
