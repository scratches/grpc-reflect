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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.FileDescriptor;

public class DescriptorParserV23Tests {

	@Test
	public void testExtensionAndExtend() throws Exception {
		String base = """
				syntax = "proto2";
				message TestMessage {
					required int32 value = 1;
					extensions 100 to 200 [verification = DECLARATION];
				}
				""";
		String input = """
				syntax = "proto3";
				import "base.proto";
				message Foo {
					string value = 1;
				}
				extend TestMessage {
					Foo foo = 100;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Cache the base.proto content to avoid re-parsing
		FileDescriptorProto baseProto = parser.resolve("base.proto", base.getBytes()).getFile(0);
		// The file with the Foo message and the extension
		FileDescriptorProto proto = parser.resolve("test.proto", input.getBytes()).getFile(1);
		// Make sure it has a Foo message ...
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("Foo");
		// ... and an extension of TestMessage
		assertThat(proto.getExtensionList()).hasSize(1);
		FieldDescriptorProto extension = proto.getExtension(0);
		assertThat(extension.getName()).isEqualTo("foo");
		assertThat(extension.getExtendee()).isEqualTo("TestMessage");
		assertThat(extension.getTypeName()).isEqualTo("Foo");
		// Now build the file descriptor and make sure it has the same content
		FileDescriptor baseFile = FileDescriptor.buildFrom(baseProto, new FileDescriptor[] {});
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] { baseFile });
		assertThat(file.getMessageTypes()).hasSize(1);
		assertThat(file.getMessageTypes().get(0).getName()).isEqualTo("Foo");
		assertThat(file.getExtensions()).hasSize(1);
		assertThat(file.getExtensions().get(0).getName()).isEqualTo("foo");
		assertThat(file.getExtensions().get(0).getContainingType().getName()).isEqualTo("TestMessage");
		assertThat(file.getExtensions().get(0).getMessageType().getName()).isEqualTo("Foo");
	}

	@Test
	public void testMethodOptionsExtended() throws Exception {
		String input = """
				syntax = "proto3";
				import "google/protobuf/descriptor.proto";
				message Foo {
					string value = 1;
				}
				extend google.protobuf.MethodOptions {
					Foo foo = 10001;
				}
				service Greeter {
					rpc SayHello (Foo) returns (Foo) {
						option (foo) = { value: "test" };
					}
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// The file with the Foo message and the extension
		FileDescriptorProto proto = parser.resolve("test.proto", input.getBytes()).getFile(1);
		// Make sure it has a Foo message ...
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("Foo");
		// ... and an extension of MethodOptions
		assertThat(proto.getExtensionList()).hasSize(1);
		FileDescriptor desc = FileDescriptor.buildFrom(parser.resolve("google/protobuf/descriptor.proto").getFile(0), new FileDescriptor[] {});
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[
				] { desc });
		assertThat(file.getMessageTypes()).hasSize(1);
		assertThat(file.getMessageTypes().get(0).getName()).isEqualTo("Foo");
		assertThat(file.getExtensions()).hasSize(1);
		assertThat(file.getExtensions().get(0).getName()).isEqualTo("foo");
		assertThat(file.getExtensions().get(0).getContainingType().getFullName()).isEqualTo("google.protobuf.MethodOptions");
		assertThat(file.getExtensions().get(0).getMessageType().getName()).isEqualTo("Foo");
		ServiceDescriptorProto service = proto.getServiceList()
				.stream()
				.filter(msg -> msg.getName().equals("Greeter"))
				.findAny()
				.get();
		assertThat(service).isNotNull();
		assertThat(service.getMethodList()).hasSize(1);
		assertThat(service.getMethod(0).getName()).isEqualTo("SayHello");
		assertThat(service.getMethod(0).getOptions().getUnknownFields().asMap()).isEmpty();
		// assertThat(service.getMethod(0).getOptions().hasExtension(AnnotationsProto.http)).isTrue();
		//assertThat(parser.findExtension("foo")).isNotNull();
	}

	@Test
	public void testHttpExtensions() {
		String input = """
				syntax = "proto3";
				package optionstest;

				import "google/api/annotations.proto";

				service Greeter {
					rpc SayHello (HelloRequest) returns (HelloReply) {
						option (google.api.http) = {
							post: "/hello"
							body: "*"
						};
					}
				}
				message HelloRequest {
					string name = 1;
				}
				message HelloReply {
					string message = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input.getBytes()).getFileList().stream()
				.filter(file -> file.getName().equals("test.proto"))
				.findAny()
				.get();
		ServiceDescriptorProto service = proto.getServiceList()
				.stream()
				.filter(msg -> msg.getName().equals("Greeter"))
				.findAny()
				.get();
		assertThat(service).isNotNull();
		assertThat(service.getMethodList()).hasSize(1);
		assertThat(service.getMethod(0).getName()).isEqualTo("SayHello");
		assertThat(service.getMethod(0).getOptions().getUnknownFields().asMap()).isEmpty();
		// assertThat(service.getMethod(0).getOptions().hasExtension(AnnotationsProto.http)).isTrue();
		// assertThat(parser.findExtension("google.api.http")).isNotNull();
	}

}
