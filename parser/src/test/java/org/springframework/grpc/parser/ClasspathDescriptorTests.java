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

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class ClasspathDescriptorTests {

	@Test
	public void testClasspathDescriptor() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Comes with the protobuf-java library:
		FileDescriptorProto proto = parser.parse("descriptor.proto",
				getClass().getResourceAsStream("/google/protobuf/empty.proto"));
		assertThat(proto.getName()).isEqualTo("descriptor.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		assertThat(proto.getMessageType(0).getName()).isEqualTo("Empty");
		assertThat(proto.getMessageType(0).getFieldList()).isEmpty();
	}

	@Test
	public void testDescriptorFromClasspath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Comes with the protobuf-java library:
		FileDescriptorProto proto = parser.resolve(Path.of("google/protobuf/empty.proto")).getFile(0);
		assertThat(proto.getName()).isEqualTo("google/protobuf/empty.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		assertThat(proto.getMessageType(0).getName()).isEqualTo("Empty");
		assertThat(proto.getMessageType(0).getFieldList()).isEmpty();
	}

	@Test
	public void testDescriptorWithImportsFromClasspath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Comes with the protobuf-java library:
		FileDescriptorSet files = parser.resolve(Path.of("google/protobuf/type.proto"));
		assertThat(files.getFileCount()).isEqualTo(3);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("google/protobuf/any.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorFromClasspathFile() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorSet files = parser.resolve(Path.of("multi/bar.proto"));
		assertThat(files.getFileCount()).isEqualTo(1);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("multi/bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorFromClasspathDirectory() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Classpath directory search
		FileDescriptorSet files = parser.resolve(Path.of("multi"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("multi/bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorFromClasspathDirectoryAndBasePath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of("multi"));
		// With a base path:
		FileDescriptorSet files = parser.resolve(Path.of(""));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

}
