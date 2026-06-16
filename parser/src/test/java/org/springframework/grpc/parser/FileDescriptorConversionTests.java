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
package org.springframework.grpc.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

public class FileDescriptorConversionTests {

	@Test
	public void testConversionFromProtobufLibrary() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorManager manager = new FileDescriptorManager();
		// This one has a lot of protobuf idioms, including extensions and options, so
		// it's a good test of the conversion
		manager.convert(parser.resolve("google/protobuf/descriptor.proto"));
	}

	@Test
	public void testV3ParseNestedEnumType() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					enum Foo {
						UNKNOWN = 0;
						FOO = 1;
						BAR = 2;
					}
					Foo foo = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorManager manager = new FileDescriptorManager();
		FileDescriptor[] files = manager.convert(parser.resolve("test.proto", input.getBytes()));
		assertThat(files).hasSize(1);
		FileDescriptor proto = files[0];
		assertThat(proto.getMessageTypes()).hasSize(1);
		Descriptor type = proto.getMessageTypes().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFields()).hasSize(2);
	}

	@Test
	public void testV2ParseNestedEnumType() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					optional string name = 1;
					enum Foo {
						UNKNOWN = 0;
						FOO = 1;
						BAR = 2;
					}
					optional Foo foo = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorManager manager = new FileDescriptorManager();
		FileDescriptor[] files = manager.convert(parser.resolve("test.proto", input.getBytes()));
		assertThat(files).hasSize(1);
		FileDescriptor proto = files[0];
		assertThat(proto.getMessageTypes()).hasSize(1);
		Descriptor type = proto.getMessageTypes().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFields()).hasSize(2);
	}

}
