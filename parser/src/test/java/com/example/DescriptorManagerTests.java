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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;

public class DescriptorManagerTests {

	@Test
	public void testDependencies() {
		String input = """
				syntax = "proto3";
				import "google/protobuf/any.proto";
				message TestMessage {
					google.protobuf.Any value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		FileDescriptorSet files = parser.resolve(proto);
		FileDescriptor[] descriptors = new FileDescriptorManager().convert(files);
		assertThat(descriptors).hasSize(2);
		FileDescriptor descriptor = descriptors[1];
		assertThat(descriptor.getName()).isEqualTo("test.proto");
		assertThat(descriptor.getMessageTypes()).hasSize(1);
		assertThat(descriptor.getMessageTypes().get(0).getName()).isEqualTo("TestMessage");
		assertThat(descriptor.getDependencies()).hasSize(1);
	}

	@Test
	public void testService() {
		String input = """
				syntax = "proto3";
				message Input {}
				message Output {}
				service Foo {
					rpc Echo (Input) returns (stream Output) {}
				};
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		FileDescriptorSet files = parser.resolve(proto);
		FileDescriptor[] descriptors = new FileDescriptorManager().convert(files);
		assertThat(descriptors).hasSize(1);
		FileDescriptor descriptor = descriptors[0];
		assertThat(descriptor.getName()).isEqualTo("test.proto");
		assertThat(descriptor.findServiceByName("Foo").findMethodByName("Echo").getInputType().getName())
			.isEqualTo("Input");
	}

	@Test
	public void testUnresolvedDependencies() {
		String input = """
				syntax = "proto3";
				import "google/protobuf/any.proto";
				message TestMessage {
					google.protobuf.Any value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		FileDescriptorSet files = FileDescriptorSet.newBuilder().addFile(proto).build();
		assertThat(assertThrows(IllegalStateException.class, () -> {
			new FileDescriptorManager().convert(files);
		}).getMessage()).contains("Missing dependency: google/protobuf/any.proto");
	}

}
