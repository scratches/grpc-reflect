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
package org.springframework.grpc.sample;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;

public class MessageConverterTests {

	@Test
	public void testConvertToPojo() {
		MessageConverter converter = new MessageConverter();
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(Foo.class);
		Descriptor desc = registry.descriptor(Foo.class);
		var foo = DynamicMessage.newBuilder(desc).setField(
				desc.findFieldByName("name"), "foo").setField(
						desc.findFieldByName("age"), 30)
				.build();

		Foo convertedFoo = converter.convert(foo, Foo.class);

		assertThat(convertedFoo).isNotNull();
		assertThat(convertedFoo.getName()).isEqualTo("foo");
		assertThat(convertedFoo.getAge()).isEqualTo(30);
	}

	@Test
	public void testConvertToMessage() {
		MessageConverter converter = new MessageConverter();
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(Foo.class);
		Descriptor desc = registry.descriptor(Foo.class);
		Foo foo = new Foo();
		foo.setName("foo");
		foo.setAge(30);

		AbstractMessage message = converter.convert(foo, desc);

		assertThat(message).isNotNull();
		assertThat(message.getField(desc.findFieldByName("name"))).isEqualTo("foo");
		assertThat(message.getField(desc.findFieldByName("age"))).isEqualTo(30);
	}
}
