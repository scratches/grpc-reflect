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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;

import jakarta.annotation.Priority;

public class DescriptorProtoExtractorTests {

	private DescriptorMapper provider = DescriptorMapper.DEFAULT_INSTANCE;

	@Test
	public void testDefaultInstance() throws Exception {
		DescriptorProto proto = provider.descriptor(Foo.class).toProto();
		assertThat(proto.getName()).isEqualTo("Foo");
		assertThat(proto.getFieldCount()).isEqualTo(2);
		assertThat(proto.getField(0).getName()).isEqualTo("name");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testExtendedInstance() throws Exception {
		DescriptorProto proto = provider.descriptor(ExtendedFoo.class).toProto();
		assertThat(proto.getName()).isEqualTo("ExtendedFoo");
		assertThat(proto.getFieldCount()).isEqualTo(3);
		assertThat(proto.getField(0).getName()).isEqualTo("name");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testExtendedAnnotatedInstance() throws Exception {
		DescriptorProto proto = provider.descriptor(ExtendedAnnotatedFoo.class).toProto();
		assertThat(proto.getName()).isEqualTo("ExtendedAnnotatedFoo");
		assertThat(proto.getFieldCount()).isEqualTo(3);
		assertThat(proto.getField(0).getName()).isEqualTo("name");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testRecordInstance() throws Exception {
		DescriptorProto proto = provider.descriptor(RecordFoo.class).toProto();
		assertThat(proto.getName()).isEqualTo("RecordFoo");
		assertThat(proto.getFieldCount()).isEqualTo(2);
		assertThat(proto.getField(0).getName()).isEqualTo("name");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testOrderedInstance() throws Exception {
		DescriptorProto proto = provider.descriptor(OrderedFoo.class).toProto();
		assertThat(proto.getName()).isEqualTo("OrderedFoo");
		assertThat(proto.getFieldCount()).isEqualTo(2);
		assertThat(proto.getField(0).getName()).isEqualTo("age");
		assertThat(proto.getField(0).getNumber()).isEqualTo(1);
		assertThat(proto.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
	}

	@Test
	public void testBadlyOrderedInstance() throws Exception {
		assertThrows(IllegalStateException.class, () -> {
			provider.descriptor(BadlyOrderedFoo.class).toProto();
		});
	}

	@Test
	public void testVoidType() throws Exception {
		DescriptorProto proto = provider.descriptor(Void.class).toProto();
		assertThat(proto.getName()).isEqualTo("Void");
		assertThat(proto.getFieldCount()).isEqualTo(0);
	}

	@Test
	public void testNestedType() throws Exception {
		DescriptorProto proto = provider.descriptor(TestNested.class).toProto();
		assertThat(proto.getName()).isEqualTo("TestNested");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		FieldDescriptorProto field = proto.getField(0);
		assertThat(field.getName()).isEqualTo("bean");
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(field.getTypeName()).isEqualTo("TestBean");
	}

	@Test
	public void testArrayType() throws Exception {
		DescriptorProto proto = provider.descriptor(TestBean.class).toProto();
		assertThat(proto.getName()).isEqualTo("TestBean");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		assertThat(proto.getField(0).getName()).isEqualTo("names");
		assertThat(proto.getField(0).getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
	}

	@Test
	public void testIterableType() throws Exception {
		DescriptorProto proto = provider.descriptor(TestList.class).toProto();
		assertThat(proto.getName()).isEqualTo("TestList");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		assertThat(proto.getField(0).getName()).isEqualTo("names");
		assertThat(proto.getField(0).getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(proto.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
	}

	@Test
	public void testMapType() throws Exception {
		DescriptorProto proto = provider.descriptor(TestMap.class).toProto();
		assertThat(proto.getName()).isEqualTo("TestMap");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		FieldDescriptorProto field = proto.getField(0);
		assertThat(field.getName()).isEqualTo("items");
		assertThat(field.getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(field.getTypeName()).isEqualTo("ItemsEntry");
	}

	@Test
	public void testBeanListType() throws Exception {
		DescriptorProto proto = provider.descriptor(TestBeanList.class).toProto();
		assertThat(proto.getName()).isEqualTo("TestBeanList");
		assertThat(proto.getFieldCount()).isEqualTo(1);
		FieldDescriptorProto field = proto.getField(0);
		assertThat(field.getName()).isEqualTo("beans");
		assertThat(field.getLabel()).isEqualTo(Label.LABEL_REPEATED);
		assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
		assertThat(field.getTypeName()).isEqualTo("TestBean");
	}

	record RecordFoo(String name, int age) {
	}

	static class ExtendedFoo extends Foo {

		private String extra;

		public String getExtra() {
			return extra;
		}

		public void setExtra(String extra) {
			this.extra = extra;
		}

	}

	static class ExtendedAnnotatedFoo extends Foo {

		@Priority(10)
		private String extra;

		public String getExtra() {
			return extra;
		}

		public void setExtra(String extra) {
			this.extra = extra;
		}

	}

	static class OrderedFoo {

		@Priority(2)
		private String name;

		@Priority(1)
		private int age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

	}

	static class BadlyOrderedFoo {

		private String name;

		// Overwites priority of name (error)
		@Priority(1)
		private int age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

	}

	static class TestNested {

		private TestBean bean;

		public TestBean getBean() {
			return bean;
		}

		public void setBean(TestBean bean) {
			this.bean = bean;
		}

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

	static class TestMap {

		private Map<String, TestBean> items = new HashMap<>();

		public Map<String, TestBean> getItems() {
			return items;
		}

	}

	static class TestBeanList {

		private List<TestBean> beans;

		public List<TestBean> getBeans() {
			return beans;
		}

		public void setBeans(List<TestBean> beans) {
			this.beans = beans;
		}

	}

}
