/*
 * Copyright 2025-current the original author or authors.
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
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;

public class JsonTests {

	private FileDescriptor file;

	private Descriptor type;

	@BeforeEach
	void setup() throws Exception {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		FileDescriptor[] files = new FileDescriptorManager().convert(parser.resolve(proto));
		this.file = files[0];
		this.type = this.file.findMessageTypeByName("TestMessage");
	}

	@Test
	public void testJsonFormat() throws Exception {
		DynamicMessage message = DynamicMessage.newBuilder(type)
			.setField(type.findFieldByName("value"), "test")
			.build();
		String json = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
		assertThat(json).isEqualTo("{\"value\":\"test\"}");
	}

	@Test
	public void testJsonParse() throws Exception {
		DynamicMessage.Builder builder = DynamicMessage.newBuilder(type);
		String json = "{\"value\":\"test\"}";
		JsonFormat.parser().merge(json, builder);
		DynamicMessage message = builder.build();
		assertThat(message.getField(type.findFieldByName("value"))).isEqualTo("test");
	}

}
