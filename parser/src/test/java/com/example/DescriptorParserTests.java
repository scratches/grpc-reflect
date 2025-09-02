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
package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class DescriptorParserTests {

	@Test
	public void testParseDescriptorError() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string foo name = 1;
					int32 age = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		assertThrows(IllegalStateException.class, () -> {
			parser.parse("test.proto", input);
		});
	}

	@Test
	public void testMissingDependency() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					int32 age = 2;
				}
				import "missing.proto";
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		assertThrows(IllegalArgumentException.class, () -> {
			parser.resolve("test.proto", input);
		});
	}

	@Test
	public void testParseSimpleDescriptor() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					int32 age = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(0).getName()).isEqualTo("name");
		assertThat(type.getField(0).getNumber()).isEqualTo(1);
		assertThat(type.getField(1).getName()).isEqualTo("age");
		assertThat(type.getField(1).getNumber()).isEqualTo(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testParseMessageType() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					Foo foo = 2;
				}
				message Foo {
					string value = 1;
					int32 count = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getMessageTypeList()).hasSize(2);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(1).getName()).isEqualTo("foo");
		assertThat(type.getField(1).getNumber()).isEqualTo(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(type.getField(1).getTypeName()).isEqualTo("Foo");
	}

	@Test
	public void testParseLabel() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					repeated string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(1);
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getName()).isEqualTo("value");
		assertThat(field.getNumber()).isEqualTo(1);
		assertThat(field.getLabel()).isEqualTo(FieldDescriptorProto.Label.LABEL_REPEATED);
	}

	@Test
	public void testParseNestedMessageType() {
		String input = """
				syntax = "proto3";
				message TestMessage {
					string name = 1;
					message Foo {
						string value = 1;
						int32 count = 2;
					}
					Foo foo = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getMessageTypeList()).hasSize(2);
		DescriptorProto type = proto.getMessageTypeList().get(1);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(1).getName()).isEqualTo("foo");
		assertThat(type.getField(1).getNumber()).isEqualTo(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(type.getField(1).getTypeName()).isEqualTo("Foo");
	}

	@Test
	public void testParseEnum() {
		String input = """
				syntax = "proto3";
				enum TestEnum {
					UNKNOWN = 0;
					FOO = 1;
					BAR = 2;
				}
				message TestMessage {
					TestEnum value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getEnumTypeList()).hasSize(1);
		EnumDescriptorProto enumType = proto.getEnumTypeList().get(0);
		assertThat(enumType.getName().toString()).isEqualTo("TestEnum");
		assertThat(enumType.getValueList()).hasSize(3);
		assertThat(enumType.getValueList().get(0).getName()).isEqualTo("UNKNOWN");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_ENUM);
		assertThat(field.getTypeName()).isEqualTo("TestEnum");
	}

	@Test
	@Disabled
	public void testParseTrickyOptions() {
		String input = """
				syntax = "proto3";
				package statustest;

				import "envoyproxy/protoc-gen-validate/validate/validate.proto";
				import "google/rpc/status.proto";

				package helloworld;

				// The greeter service definition.
				service Greeter {
					// Sends a greeting
					rpc SayHello (HelloRequest) returns (HelloReply) {
						option (google.api.http) = {
							post: "/service/hello"
							body: "*"
						};
					}
				}
				// The request message containing the user's name.
				message HelloRequest {
					string name = 1  [(validate.rules).string.pattern = "^\\\\w+( +\\\\w+)*$"]; // Required. Allows multiple words with spaces in between, as it can contain both first and last name;
				}

				// The response message containing the greetings
				message HelloReply {
					string message = 1;
					google.rpc.Status status = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		DescriptorProto type = proto.getMessageTypeList().get(1);
		assertThat(type.getName().toString()).isEqualTo("HelloReply");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(type.getField(1).getTypeName()).isEqualTo("google.rpc.Status");
	}

	@Test
	public void testParseImport() {
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
		assertThat(proto.getDependencyList()).hasSize(1);
		assertThat(proto.getDependency(0)).isEqualTo("google/protobuf/any.proto");
		// The Any type is defined in the imported file, so it should not be in
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(field.getTypeName()).isEqualTo("google.protobuf.Any");
		proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("google/protobuf/any.proto");
	}

	@Test
	public void testParsePackage() {
		String input = """
				syntax = "proto3";
				package sample;
				message TestMessage {
					string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.parse("test.proto", input);
		assertThat(proto.getPackage()).isEqualTo("sample");
	}

}
