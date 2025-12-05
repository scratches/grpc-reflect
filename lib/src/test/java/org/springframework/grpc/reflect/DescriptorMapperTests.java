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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class DescriptorMapperTests {

	private DescriptorMapper mapper = DescriptorMapper.DEFAULT_INSTANCE;

	@Test
	public void testRegisterType() throws Exception {
		assertThat(mapper.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
	}

	@Test
	public void testRegisterNestedType() throws Exception {
		Descriptor descriptor = mapper.descriptor(TestNested.class);
		assertThat(descriptor.getFullName()).isEqualTo("TestNested");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		Descriptor nestedDescriptor = descriptor.getFields().get(0).getMessageType();
		assertThat(nestedDescriptor.getFullName()).isEqualTo("TestBean");
		assertThat(nestedDescriptor.getFields().size()).isEqualTo(1);
	}

	@Test
	public void testRegisterListType() throws Exception {
		Descriptor descriptor = mapper.descriptor(TestList.class);
		assertThat(descriptor.getFullName()).isEqualTo("TestList");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		FieldDescriptor field = descriptor.getFields().get(0);
		assertThat(field.getName()).isEqualTo("names");
		assertThat(field.getType()).isEqualTo(FieldDescriptor.Type.STRING);
		assertThat(field.isRepeated()).isTrue();
	}

	@Test
	public void testRegisterMapType() throws Exception {
		Descriptor descriptor = mapper.descriptor(TestMap.class);
		assertThat(descriptor.getFullName()).isEqualTo("TestMap");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		FieldDescriptor field = descriptor.getFields().get(0);
		assertThat(field.getName()).isEqualTo("items");
		assertThat(field.getType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
		assertThat(field.isRepeated()).isTrue();
		Descriptor nestedDescriptor = descriptor.getFields().get(0).getMessageType();
		assertThat(nestedDescriptor.getFullName()).isEqualTo("TestMap.ItemsEntry");
		assertThat(nestedDescriptor.getFields().size()).isEqualTo(2);
	}

	@Test
	public void testRegisterListOfMessageType() throws Exception {
		Descriptor descriptor = mapper.descriptor(TestBeanList.class);
		assertThat(descriptor.getFullName()).isEqualTo("TestBeanList");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		FieldDescriptor field = descriptor.getFields().get(0);
		assertThat(field.getName()).isEqualTo("beans");
		assertThat(field.getType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
		assertThat(field.isRepeated()).isTrue();
		Descriptor nestedDescriptor = descriptor.getFields().get(0).getMessageType();
		assertThat(nestedDescriptor.getFullName()).isEqualTo("TestBean");
		assertThat(nestedDescriptor.getFields().size()).isEqualTo(1);
	}

	@Test
	public void testRegisterTree() throws Exception {
		Descriptor descriptor = mapper.descriptor(Tree.class);
		assertThat(descriptor.getFullName()).isEqualTo("Tree");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		FieldDescriptor field = descriptor.getFields().get(0);
		assertThat(field.getName()).isEqualTo("children");
		assertThat(field.getType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
		assertThat(field.isRepeated()).isTrue();
		Descriptor nestedDescriptor = descriptor.getFields().get(0).getMessageType();
		assertThat(nestedDescriptor.getFullName()).isEqualTo("Tree");
		assertThat(nestedDescriptor.getFields().size()).isEqualTo(1);
	}

	@Test
	public void testRegisterCycle() throws Exception {
		Descriptor descriptor = mapper.descriptor(Cycle.class);
		assertThat(descriptor.getFullName()).isEqualTo("Cycle");
		assertThat(descriptor.getFields().size()).isEqualTo(1);
		FieldDescriptor field = descriptor.getFields().get(0);
		assertThat(field.getName()).isEqualTo("nested");
		assertThat(field.getType()).isEqualTo(FieldDescriptor.Type.MESSAGE);
		Descriptor nestedDescriptor = field.getMessageType();
		assertThat(nestedDescriptor.getFullName()).isEqualTo("Nested");
		assertThat(nestedDescriptor.getFields().size()).isEqualTo(1);
		Descriptor other = mapper.descriptor(Nested.class);
		assertThat(nestedDescriptor).isEqualTo(other);
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

	static class TestBeanList {

		private List<TestBean> beans;

		public List<TestBean> getBeans() {
			return beans;
		}

		public void setBeans(List<TestBean> beans) {
			this.beans = beans;
		}

	}

	static class TestMap {

		private Map<String, TestBean> items = new HashMap<>();

		public Map<String, TestBean> getItems() {
			return items;
		}

	}

	static class Tree {

		private List<Tree> children = new ArrayList<>();

		public List<Tree> getChildren() {
			return children;
		}

	}

	static class Nested {
		private Cycle cycle;

		public Cycle getCycle() {
			return cycle;
		}

		public void setCycle(Cycle cycle) {
			this.cycle = cycle;
		}
	}

	static class Cycle {

		private Nested nested;

		public Nested getNested() {
			return nested;
		}

		public void setNested(Nested nested) {
			this.nested = nested;
		}
	}

}
