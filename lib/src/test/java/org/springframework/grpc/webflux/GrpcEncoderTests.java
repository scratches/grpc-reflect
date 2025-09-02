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

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.util.MimeType;

public class GrpcEncoderTests {

	@Test
	void testEncoder() {
		GrpcEncoder encoder = new GrpcEncoder();
		HelloReply message = HelloReply.newBuilder().setMessage("Hello World").build();
		encoder.encodeValue(message, DefaultDataBufferFactory.sharedInstance, ResolvableType.forClass(HelloReply.class),
				MimeType.valueOf("application/grpc"), null);
	}

}
