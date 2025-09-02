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

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

public class ServiceDescriptorTests {

	@Test
	public void testParseUnaryService() {
		String input = """
				syntax = "proto3";
				message Input {
					string value = 1;
				}
				message Output {
					string value = 1;
				}
				service Foo {
					rpc Echo (Input) returns (Output) {}
				};
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getServiceList()).hasSize(1);
		ServiceDescriptorProto service = proto.getService(0);
		assertThat(service.getName()).isEqualTo("Foo");
		assertThat(service.getMethodCount()).isEqualTo(1);
		assertThat(service.getMethod(0).getName()).isEqualTo("Echo");
		assertThat(service.getMethod(0).getInputType()).isEqualTo("Input");
		assertThat(service.getMethod(0).getOutputType()).isEqualTo("Output");
		assertThat(proto.getMessageTypeList()).hasSize(2);
	}

	@Test
	public void testParseUnaryServiceMultipleRpc() {
		String input = """
				syntax = "proto3";
				service Foo {
					rpc Echo (Input) returns (Output) {}
					rpc Blah (Any) returns (Output) {}
				};
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		ServiceDescriptorProto service = proto.getService(0);
		assertThat(service.getMethodCount()).isEqualTo(2);
		assertThat(service.getName()).isEqualTo("Foo");
		assertThat(service.getMethod(1).getName()).isEqualTo("Blah");
		assertThat(service.getMethod(1).getInputType()).isEqualTo("Any");
	}

	@Test
	public void testParseStreamingService() {
		String input = """
				syntax = "proto3";
				service Foo {
					rpc Echo (Input) returns (stream Output) {}
				};
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		ServiceDescriptorProto service = proto.getService(0);
		assertThat(service.getMethodList()).hasSize(1);
		assertThat(service.getMethod(0).getName()).isEqualTo("Echo");
		assertThat(service.getMethod(0).getInputType()).isEqualTo("Input");
		assertThat(service.getMethod(0).getOutputType()).isEqualTo("Output");
		assertThat(service.getMethod(0).getClientStreaming()).isFalse();
		assertThat(service.getMethod(0).getServerStreaming()).isTrue();
	}

}
