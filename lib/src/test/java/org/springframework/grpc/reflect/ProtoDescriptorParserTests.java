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

public class ProtoDescriptorParserTests {

	@Test
	public void testDescriptorFile() {
		ProtoDescriptorParser parser = new ProtoDescriptorParser();
		FileDescriptorSet files = parser.resolve(null, "proto/simple.proto");
		assertThat(files.getFileCount()).isEqualTo(1);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("proto/simple.proto");
		assertThat(proto.getMessageTypeList()).hasSize(2);
	}

	@Test
	public void testDescriptorFileUrl() {
		ProtoDescriptorParser parser = new ProtoDescriptorParser();
		FileDescriptorSet files = parser.resolve("file:src/test/resources", "proto/simple.proto", "proto/bar.proto");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(1);
		assertThat(proto.getName()).isEqualTo("proto/bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testDescriptorDirectory() {
		ProtoDescriptorParser parser = new ProtoDescriptorParser();
		FileDescriptorSet files = parser.resolve("file:src/test/resources", "proto");
		assertThat(files.getFileCount()).isEqualTo(2);
		assertThat(files.getFileList()).extracting("name").containsOnlyOnce("proto/bar.proto");
	}

	@Test
	public void testDescriptorPattern() {
		ProtoDescriptorParser parser = new ProtoDescriptorParser();
		// Classpath directory search
		FileDescriptorSet files = parser.resolve(null, "proto/**/*.proto");
		assertThat(files.getFileCount()).isEqualTo(2);
		FileDescriptorProto proto = files.getFile(0);
		assertThat(proto.getName()).isEqualTo("proto/bar.proto");
		assertThat(proto.getMessageTypeList()).hasSize(1);
	}

	@Test
	public void testNotProtoDescriptorFile() {
		ProtoDescriptorParser parser = new ProtoDescriptorParser();
		assertThat(new File("src/test/resources/binary/multi.pb").exists()).isTrue();
		FileDescriptorSet files = parser.resolve(null, "binary/multi.pb");
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
