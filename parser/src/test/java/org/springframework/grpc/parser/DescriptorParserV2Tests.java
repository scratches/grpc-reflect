/*
 * Copyright 2025-current the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *			https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.grpc.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class DescriptorParserV2Tests {

	@Test
	public void testParseRequiredField() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					required string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(1);
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getName()).isEqualTo("value");
		assertThat(field.getNumber()).isEqualTo(1);
	}

	@Test
	public void testParseDescriptorError() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					string foo name = 1;
					int32 age = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		assertThrows(IllegalStateException.class, () -> {
			parser.resolve("test.proto", input);
		});
	}

	@Test
	public void testMissingDependency() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					optional string name = 1;
					optional int32 age = 2;
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
				syntax = "proto2";
				message TestMessage {
					optional string name = 1;
					optional int32 age = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
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
				syntax = "proto2";
				message TestMessage {
					optional string name = 1;
					optional Foo foo = 2;
				}
				message Foo {
					optional string value = 1;
					optional int32 count = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
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
				syntax = "proto2";
				message TestMessage {
					repeated string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
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
				syntax = "proto2";
				message TestMessage {
					optional string name = 1;
					message Foo {
						optional string value = 1;
						optional int32 count = 2;
					}
					optional Foo foo = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
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
				syntax = "proto2";
				enum TestEnum {
					UNKNOWN = 0;
					FOO = 1;
					BAR = 2;
				}
				message TestMessage {
					optional TestEnum value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
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
	public void testParseImport() {
		String input = """
				syntax = "proto2";
				import "google/protobuf/any.proto";
				message TestMessage {
					optional google.protobuf.Any value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(1);
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
				syntax = "proto2";
				package sample;
				message TestMessage {
					optional string value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getPackage()).isEqualTo("sample");
	}

	@Test
	public void testParseMapType() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					map<string, Foo> names = 1;
				}
				message Foo {
					optional string value = 1;
					optional int32 count = 2;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getMessageTypeList()).hasSize(2);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(1);
		assertThat(type.getField(0).getName()).isEqualTo("names");
		assertThat(type.getField(0).getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(type.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(type.getField(0).getTypeName()).isEqualTo("NamesEntry");
		// System.err.println(type.getNestedType(0));
		type = type.getNestedType(0);
		assertThat(type.getName()).isEqualTo("NamesEntry");
		assertThat(type.getOptions().getMapEntry()).isTrue();
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(0).getName()).isEqualTo("key");
		assertThat(type.getField(1).getName()).isEqualTo("value");
		assertThat(type.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
	}

	@Test
	public void testParseFieldOption() {
		String input = """
				syntax = "proto2";
				message TestMessage {
					repeated string value = 1 [retention = RETENTION_SOURCE];
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		assertThat(type.getFieldList()).hasSize(1);
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getName()).isEqualTo("value");
		assertThat(field.getNumber()).isEqualTo(1);
		// TODO: check the actual option
		// assertThat(field.getOptions().getAllFields().size()).isEqualTo(1);
	}


	@Test
	public void testParseExtension() {
		String input = """
				syntax = "proto2";
				message Foo {
					extensions 4 to 1000 [
						declaration = {
								number: 4
								full_name: ".my.package.event_annotations"
								type: ".logs.proto.ValidationAnnotations"
								repeated: true },
						declaration = {
							number: 999
							full_name: ".foo.package.bar"
							type: "int32"}];
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("Foo");
		// TODO: check the extensions
		// assertThat(type.getExtensionRangeList()).hasSize(1);
	}

	@Test
	public void testParseEnumKeyword() {
		String input = """
				syntax = "proto2";
				enum TestEnum {
					DECLARATION = 0;
					UNVERIFIED = 1;
				}
				message TestMessage {
					optional TestEnum value = 1;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getEnumTypeList()).hasSize(1);
		EnumDescriptorProto enumType = proto.getEnumTypeList().get(0);
		assertThat(enumType.getName().toString()).isEqualTo("TestEnum");
		assertThat(enumType.getValueList()).hasSize(2);
		assertThat(enumType.getValueList().get(0).getName()).isEqualTo("DECLARATION");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageTypeList().get(0);
		assertThat(type.getName().toString()).isEqualTo("TestMessage");
		FieldDescriptorProto field = type.getField(0);
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_ENUM);
		assertThat(field.getTypeName()).isEqualTo("TestEnum");
	}

	@Test
	public void testParseDescriptor() {
		String input = """
				syntax = "proto2";
				message Foo {
					message Declaration {
					}
					repeated Declaration declaration = 2;
					optional FeatureSet features = 50;
					enum VerificationState {
						DECLARATION = 0;
						UNVERIFIED = 1;
					}
					optional VerificationState verification = 3;
				}
				""";
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorProto proto = parser.resolve("test.proto", input).getFile(0);
		assertThat(proto.getMessageTypeList()).hasSize(2);
		DescriptorProto type = proto.getMessageTypeList().get(1);
		assertThat(type.getName().toString()).isEqualTo("Foo");
		// TODO: check the extensions
		// assertThat(type.getExtensionRangeList()).hasSize(1);
	}

}
