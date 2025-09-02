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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;

public class DescriptorProtoProviderTests {

	@Test
	public void testDefaultInstance() throws Exception {
		DescriptorProtoExtractor provider = DescriptorProtoExtractor.DEFAULT_INSTANCE;
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
		DescriptorProtoExtractor provider = DescriptorProtoExtractor.DEFAULT_INSTANCE;
		DescriptorProto proto = provider.proto(Void.class);
		assertThat(proto.getName()).isEqualTo("Void");
		assertThat(proto.getFieldCount()).isEqualTo(0);
	}

	@Test
	public void testArrayType() throws Exception {
		DescriptorProtoExtractor provider = DescriptorProtoExtractor.DEFAULT_INSTANCE;
		DescriptorProto proto = provider.proto(TestBean.class);
		assertThat(proto.getName()).isEqualTo("TestBean");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		assertThat(proto.getField(0).getName()).isEqualTo("names");
		assertThat(proto.getField(0).getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
	}

	@Test
	public void testIterableType() throws Exception {
		DescriptorProtoExtractor provider = DescriptorProtoExtractor.DEFAULT_INSTANCE;
		DescriptorProto proto = provider.proto(TestList.class);
		assertThat(proto.getName()).isEqualTo("TestList");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		assertThat(proto.getField(0).getName()).isEqualTo("names");
		assertThat(proto.getField(0).getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
	}

	static class TestBean {

		private String[] names;

		public String[] getNames() {
			return names;
		}

		public void setNames(String[] names) {
			this.names = names;
		}

	}

	static class TestList {

		private List<String> names;

		public List<String> getNames() {
			return names;
		}

		public void setNames(List<String> names) {
			this.names = names;
		}

	}

}
