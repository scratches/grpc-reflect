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
package org.springframework.grpc.reflect;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class BinaryDescriptorParserTests {

	@Test
	public void testDescriptorFile() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		FileDescriptorSet files = parser.resolve(null, "binary/multi.pb");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorDuplicateFile() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		FileDescriptorSet files = parser.resolve("", "binary/multi.pb", "file:src/test/resources/binary/multi.pb");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorFileUrl() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		FileDescriptorSet files = parser.resolve(null, "file:target/multi.pb");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	public void testDescriptorDirectory() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		// Classpath directory search
		FileDescriptorSet files = parser.resolve(null, "binary/");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorPattern() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		// Classpath directory search
		FileDescriptorSet files = parser.resolve(null, "binary/**/*.pb");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testValidateExample() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		FileDescriptorSet files = parser.resolve("", "file:target/validate.pb");
		assertThat(files.getFileCount()).isEqualTo(1);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).endsWith("protobuf/validate.proto");
		assertThat(proto.getMessageTypeList()).hasSize(23);
		FieldDescriptorProto field = field(proto, ".google.protobuf.MessageOptions", "disabled");
		assertThat(field.getNumber()).isEqualTo(1071);
	}

	@Test
	public void testNotBinaryDescriptorFile() {
		BinaryDescriptorParser parser = new BinaryDescriptorParser();
		assertThat(new File("src/test/resources/proto/bar.proto").exists()).isTrue();
		FileDescriptorSet files = parser.resolve(null, "proto/bar.proto");
		assertThat(files.getFileCount()).isEqualTo(0);
	}

	FieldDescriptorProto field(FileDescriptorProto proto, String name, String field) {
		for (var message : proto.getExtensionList()) {
			if (message.getName().equals(field) && message.getExtendee().equals(name)) {
				return message;
			}
		}
		throw new IllegalStateException("Extension not found: " + name + "." + field);
	}

}
