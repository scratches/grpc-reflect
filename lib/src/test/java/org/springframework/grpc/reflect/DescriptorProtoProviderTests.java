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
package org.springframework.grpc.reflect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.grpc.reflect.DescriptorProtoProvider;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;

public class DescriptorProtoProviderTests {
	@Test
	public void testDefaultInstance() throws Exception {
		DescriptorProtoProvider provider = DescriptorProtoProvider.DEFAULT_INSTANCE;
		DescriptorProto proto = provider.proto(Foo.class);
		assertThat(proto.getName()).isEqualTo("Foo");
		assertThat(proto.getFieldCount()).isEqualTo(2);
		assertThat(proto.getField(0).getName()).isEqualTo("name");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testVoidType() throws Exception {
		DescriptorProtoProvider provider = DescriptorProtoProvider.DEFAULT_INSTANCE;
		DescriptorProto proto = provider.proto(Void.class);
		assertThat(proto.getName()).isEqualTo("Void");
		assertThat(proto.getFieldCount()).isEqualTo(0);
	}
}
