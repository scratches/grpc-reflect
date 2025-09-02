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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class FileSystemDescriptorTests {

	@Test
	public void testDescriptorWithImportsFromBasepath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/deps"));
		FileDescriptorSet files = parser.resolve(Path.of("foo.proto"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("foo.proto");
		assertThat(proto.getDependencyList()).containsExactly("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(0);
	}

	@Test
	public void testDescriptorWithImportedEnum() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/enums"));
		FileDescriptorSet files = parser.resolve(Path.of("foo.proto"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getEnumTypeList()).hasSize(1);
		proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("foo.proto");
		assertThat(proto.getDependencyList()).containsExactly("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageType(0);
		assertThat(type.getName()).isEqualTo("EchoRequest");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(0).getName()).isEqualTo("type");
		assertThat(type.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_ENUM);
	}

	@Test
	public void testDescriptorWithImportedEnumWithPackage() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/pkgs"));
		FileDescriptorSet files = parser.resolve(Path.of("foo.proto"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getEnumTypeList()).hasSize(1);
		proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("foo.proto");
		assertThat(proto.getDependencyList()).containsExactly("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		DescriptorProto type = proto.getMessageType(0);
		assertThat(type.getName()).isEqualTo("EchoRequest");
		assertThat(type.getFieldList()).hasSize(2);
		assertThat(type.getField(0).getName()).isEqualTo("type");
		assertThat(type.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_ENUM);
		assertThat(type.getField(0).getTypeName()).isEqualTo("sample.EchoType");
	}

	@Test
	public void testMultiDescriptor() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/multi"));
		FileDescriptorSet files = parser.resolve(Path.of("foo.proto"), Path.of("bar.proto"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("foo.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testMultiDescriptorScan() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/multi"));
		FileDescriptorSet files = parser.resolve(Path.of("."));
		assertThat(files.getFileCount()).isEqualTo(2);
		files.getFileList().forEach(file -> {
			assertThat(file.getName()).isIn("foo.proto", "bar.proto");
			assertThat(file.getMessageTypeList()).hasSize(1);
		});
	}

	@Test
	public void testMultiDescriptorScanIncludingImports() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("src/test/proto/deps"));
		FileDescriptorSet files = parser.resolve(Path.of("."));
		assertThat(files.getFileCount()).isEqualTo(2);
		files.getFileList().forEach(file -> {
			assertThat(file.getName()).isIn("foo.proto", "bar.proto");
		});
	}

}
