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
import org.springframework.grpc.sample.proto.HelloRequest;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;

public class MessageConverterTests {

	private DescriptorProvider registry = DescriptorProvider.DEFAULT_INSTANCE;;

	@Test
	public void testConvertGeneratedTypeToPojo() {
		;
		MessageConverter converter = new MessageConverter(registry);
		Descriptor desc = registry.descriptor(Foo.class);
		var foo = DynamicMessage.newBuilder(desc)
				.setField(desc.findFieldByName("name"), "foo")
				.build();

		Foo convertedFoo = converter.convert(foo, Foo.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getName()).isEqualTo("foo");
		// The age field is not included in the schema, so it should be 0
		assertThat(convertedFoo.getAge()).isEqualTo(0);
	}

	@Test
	public void testConvertToPojo() {
		;
		MessageConverter converter = new MessageConverter(registry);
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
		MessageConverter converter = new MessageConverter(registry);
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
	public void testConvertToVoid() {
		MessageConverter converter = new MessageConverter(registry);
		var foo = DynamicMessage.newBuilder(registry.descriptor(Void.class))
				.build();
		Object message = converter.convert(foo, Void.class);

		assertThat(message).isNull();
	}

	@Test
	public void testConvertToGeneratedMessage() {
		DescriptorRegistrar catalog = new DescriptorRegistrar();
		catalog.register(Foo.class, HelloRequest.getDescriptor());
		MessageConverter converter = new MessageConverter(catalog);
		Descriptor desc = HelloRequest.getDescriptor();
		Foo foo = new Foo();
		foo.setName("foo");
		foo.setAge(30);

		// TODO: a MessageConverter ought to be able to convert a Foo into a number of
		// different message types, so maybe it needs a method with a Descriptor
		// argument?
		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		assertThat(message.getField(desc.findFieldByName("name"))).isEqualTo("foo");
		// Age is not in the schema
		assertThat(message.getAllFields().size()).isEqualTo(1);
	}

	@Test
	public void testConvertToMessage() {
		MessageConverter converter = new MessageConverter(registry);
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
		MessageConverter converter = new MessageConverter(registry);
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
		MessageConverter converter = new MessageConverter(registry);

		AbstractMessage message = converter.convert(null, (Descriptor) null);

		assertThat(message).isNotNull();
		assertThat(message.getDescriptorForType().getFields()).isEmpty();
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
}
