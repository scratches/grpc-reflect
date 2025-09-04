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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.grpc.sample.proto.HelloRequest;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;

public class MessageConverterTests {

	private DescriptorProvider registry = DescriptorProvider.DEFAULT_INSTANCE;

	@Test
	public void testConvertGeneratedTypeToPojo() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Foo.class);
		var foo = DynamicMessage.newBuilder(desc).setField(desc.findFieldByName("name"), "foo").build();

		Foo convertedFoo = converter.convert(foo, Foo.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getName()).isEqualTo("foo");
		// The age field is not included in the schema, so it should be 0
		assertThat(convertedFoo.getAge()).isEqualTo(0);
	}

	@Test
	public void testConvertToPojo() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Foo.class);
		var foo = DynamicMessage.newBuilder(desc)
			.setField(desc.findFieldByName("name"), "foo")
			.setField(desc.findFieldByName("age"), 30)
			.build();

		Foo convertedFoo = converter.convert(foo, Foo.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getName()).isEqualTo("foo");
		assertThat(convertedFoo.getAge()).isEqualTo(30);
	}

	@Test
	public void testConvertToPojoWithNested() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Foo.class);
		var foo = DynamicMessage.newBuilder(desc)
			.setField(desc.findFieldByName("name"), "foo")
			.setField(desc.findFieldByName("age"), 30)
			.build();
		var bar = DynamicMessage.newBuilder(registry.descriptor(Bar.class))
			.setField(registry.descriptor(Bar.class).findFieldByName("foo"), foo)
			.build();

		Foo convertedFoo = converter.convert(bar, Bar.class).getFoo();

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getName()).isEqualTo("foo");
		assertThat(convertedFoo.getAge()).isEqualTo(30);
	}

	@Test
	public void testConvertToPojoWithMap() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Spam.class);
		Spam input = new Spam();
		input.getValues().put("foo", "bar");
		input.getValues().put("hello", "world");
		var foo = converter.convert(input, desc);

		Spam convertedFoo = converter.convert(foo, Spam.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getValues().get("foo")).isEqualTo("bar");
		assertThat(convertedFoo.getValues().get("hello")).isEqualTo("world");
	}

	@Test
	public void testConvertToPojoWithList() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Bucket.class);
		Bucket input = new Bucket();
		input.getValues().add("foo");
		input.getValues().add("bar");
		var foo = converter.convert(input, desc);

		Bucket convertedFoo = converter.convert(foo, Bucket.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getValues().get(0)).isEqualTo("foo");
		assertThat(convertedFoo.getValues().get(1)).isEqualTo("bar");
	}

	@Test
	public void testConvertToVoid() {
		MessageConverter converter = new MessageConverter();
		var foo = DynamicMessage.newBuilder(registry.descriptor(Void.class)).build();
		Object message = converter.convert(foo, Void.class);

		assertThat(message).isNull();
	}

	@Test
	public void testConvertToGeneratedMessage() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = HelloRequest.getDescriptor();
		Foo foo = new Foo();
		foo.setName("foo");
		foo.setAge(30);

		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		assertThat(message.getField(desc.findFieldByName("name"))).isEqualTo("foo");
		// Age is not in the schema
		assertThat(message.getAllFields().size()).isEqualTo(1);
	}

	@Test
	public void testConvertToMessage() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Foo.class);
		Foo foo = new Foo();
		foo.setName("foo");
		foo.setAge(30);

		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		assertThat(message.getField(desc.findFieldByName("name"))).isEqualTo("foo");
		assertThat(message.getField(desc.findFieldByName("age"))).isEqualTo(30);
	}

	@Test
	public void testConvertToNestedMessage() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Foo.class);
		Foo foo = new Foo();
		foo.setName("foo");
		foo.setAge(30);
		Bar bar = new Bar();
		bar.setFoo(foo);

		AbstractMessage message = converter.convert(bar, registry.descriptor(Bar.class));

		assertThat(message).isNotNull();
		AbstractMessage nestedMessage = (AbstractMessage) message
			.getField(registry.descriptor(Bar.class).findFieldByName("foo"));
		assertThat(nestedMessage.getField(desc.findFieldByName("name"))).isEqualTo("foo");
		assertThat(nestedMessage.getField(desc.findFieldByName("age"))).isEqualTo(30);
	}

	@Test
	public void testConvertVoidToMessage() {
		MessageConverter converter = new MessageConverter();

		AbstractMessage message = converter.convert(null, (Descriptor) null);

		assertThat(message).isNotNull();
		assertThat(message.getDescriptorForType().getFields()).isEmpty();
	}

	@Test
	public void testConvertToMessageWithMap() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Spam.class);
		Spam foo = new Spam();
		foo.getValues().put("foo", "bar");

		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		@SuppressWarnings("unchecked")
		DynamicMessage field = ((Iterable<DynamicMessage>) message.getField(desc.findFieldByName("values"))).iterator()
			.next();
		assertThat(field.getField(field.getDescriptorForType().findFieldByName("key"))).isEqualTo("foo");
		assertThat(field.getField(field.getDescriptorForType().findFieldByName("value"))).isEqualTo("bar");
	}

	@Test
	public void testConvertToMessageWithList() {
		MessageConverter converter = new MessageConverter();
		Descriptor desc = registry.descriptor(Bucket.class);
		Bucket foo = new Bucket();
		foo.getValues().add("foo");
		foo.getValues().add("bar");

		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		@SuppressWarnings("unchecked")
		Iterator<String> field = ((Iterable<String>) message.getField(desc.findFieldByName("values"))).iterator();
		assertThat(field.next()).isEqualTo("foo");
		assertThat(field.next()).isEqualTo("bar");
	}

	static class Bar {

		private Foo foo;

		public Foo getFoo() {
			return foo;
		}

		public void setFoo(Foo foo) {
			this.foo = foo;
		}

	}

	static class Spam {

		private Map<String, String> values = new HashMap<>();

		public Map<String, String> getValues() {
			return values;
		}

	}

	static class Bucket {

		private List<String> values = new ArrayList<>();

		public List<String> getValues() {
			return values;
		}

	}

}
