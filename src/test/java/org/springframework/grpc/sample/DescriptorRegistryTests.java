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
import org.springframework.util.ClassUtils;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;

public class DescriptorRegistryTests {

	@Test
	public void testRegisterMethod() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry(
				clazz -> DescriptorProto.newBuilder().setName(clazz.getSimpleName()).build(),
				method -> MethodDescriptorProto.newBuilder().setName("echo").setOutputType("Foo").setInputType("Foo")
						.build());
		registry.register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
	}

	@Test
	public void testRegisterType() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry(
				clazz -> DescriptorProto.newBuilder().setName(clazz.getSimpleName()).build(),
				method -> null);
		registry.register(Foo.class);
		registry.register(Bar.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(registry.descriptor(Bar.class)).isNotNull();
	}

	public static class Bar {
	}

	public Bar translate(Foo foo) {
		return new Bar();
	}

	public Foo echo(Foo foo) {
		return foo;
	}
}
