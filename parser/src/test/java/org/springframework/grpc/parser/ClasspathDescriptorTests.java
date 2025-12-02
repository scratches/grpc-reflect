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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.grpc.parser.PathLocator.NamedBytes;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class ClasspathDescriptorTests {

	@Test
	public void testClasspathDescriptor() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		// Comes with the protobuf-java library:
		FileDescriptorProto proto = parser.resolve("descriptor.proto",
				getClass().getResourceAsStream("/google/protobuf/empty.proto")).getFile(0);
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

	private NamedBytes[] multi(String path) {
		if (path.equals("multi")) {
			return new NamedBytes[] { new NamedBytes("multi/bar.proto", () -> single("multi/bar.proto")),
					new NamedBytes("multi/foo.proto", () -> single("multi/foo.proto")) };
		}
		return new NamedBytes[0];
	}

	private NamedBytes[] base(String path) {
		if (path.equals("")) {
			return new NamedBytes[] { new NamedBytes("bar.proto", () -> single("multi/bar.proto")),
					new NamedBytes("foo.proto", () -> single("multi/foo.proto")) };
		}
		return new NamedBytes[0];
	}

	private byte[] single(String path) {
		try {
			return getClass().getClassLoader().getResourceAsStream(path).readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read resource: " + path, e);
		}
	}

	@Test
	public void testDescriptorFromClasspathDirectory() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		parser.setPathLocator(this::multi);
		// Classpath directory search
		FileDescriptorSet files = parser.resolve(Path.of("multi"));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("multi/bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorFromClasspathDirectoryAndBasePath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		parser.setPathLocator(this::base);
		// With a base path:
		FileDescriptorSet files = parser.resolve(Path.of(""));
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorV2() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorSet files = parser.resolve(Path.of("protobuf/descriptor.proto"));
		assertThat(files.getFileCount()).isEqualTo(1);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("protobuf/descriptor.proto");
		assertThat(proto.getMessageTypeList()).hasSize(34);
	}

	@Test
	public void testValidatorV2() {
		// TODO: handle extend keyword instead of ignoring it
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorSet files = parser.resolve(Path.of("protobuf/validate.proto"));
		assertThat(files.getFileCount()).isEqualTo(4);
		FileDescriptorProto proto = files.getFile(3);
		assertThat(proto.getName()).isEqualTo("protobuf/validate.proto");
		assertThat(proto.getMessageTypeList()).hasSize(23);
	}

}
