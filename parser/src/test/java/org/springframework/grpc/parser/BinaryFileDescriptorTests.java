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

import org.junit.jupiter.api.Test;
import org.springframework.grpc.parser.PathLocator.NamedBytes;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class BinaryFileDescriptorTests {

	@Test
	public void testDescriptorWithImportsFromBasepath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		parser.setPathLocator(new DefaultPathLocator("src/test/proto/binary"));
		FileDescriptorSet files = parser.resolve("foo.pb");
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
	public void testDescriptorWithImportsFromClasspath() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		FileDescriptorSet files = parser.resolve("binary/multi.pb");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
		proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("foo.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	private NamedBytes[] binary(String path) {
		if (path.equals("binary")) {
			return new NamedBytes[] { new NamedBytes("multi.pb", () -> single("binary/multi.pb")) };
		}
		return new NamedBytes[0];
	}

	private byte[] single(String path) {
		try {
			return getClass().getClassLoader().getResourceAsStream(path).readAllBytes();
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to read resource: " + path, e);
		}
	}

	@Test
	public void testDescriptorFromClasspathDirectory() {
		FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
		parser.setPathLocator(this::binary);
		// Classpath directory search
		FileDescriptorSet files = parser.resolve("binary");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

}
