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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;

public class DescriptorRegistryTests {

	@Test
	public void testRegisterMethod() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry(
				clazz -> DescriptorProto.newBuilder().setName(clazz.getSimpleName()).build(),
				method -> MethodDescriptorProto.newBuilder().setName("Echo").setOutputType("Foo").setInputType("Foo")
						.build());
		registry.register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
	}

	private MethodDescriptor method(DescriptorRegistry registry, String fullMethodName) {
			String serviceName = fullMethodName.substring(0, fullMethodName.lastIndexOf('/'));
			String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
			FileDescriptor file = registry.file(serviceName);
			if (file == null) {
				return null;
			}
			for (MethodDescriptor method : file.findServiceByName(serviceName).getMethods()) {
				if (method.getName().equals(methodName)) {
					return method;
				}
			}
			return null;
	}

	@Test
	public void testRegisterTwoMethods() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		registry.register(DescriptorRegistryTests.class.getMethod("translate", Foo.class));
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		assertThat(registry.descriptor(Bar.class).getFullName()).isEqualTo("Bar");
		assertThat(method(registry, "DescriptorRegistryTests/Translate")).isNotNull();
	}

	@Test
	public void testRegisterUnownedMethod() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register("Service/Spam", Foo.class, Bar.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(registry.descriptor(Bar.class).getFullName()).isEqualTo("Bar");
		assertThat(method(registry, "Service/Spam")).isNotNull();
	}

	@Test
	public void testRegisterTwoUnownedMethods() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register("Service/Echo", Foo.class, Foo.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "Service/Echo")).isNotNull();
		registry.register("Service/Spam", Foo.class, Bar.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "Service/Echo")).isNotNull();
		assertThat(registry.descriptor(Bar.class).getFullName()).isEqualTo("Bar");
		assertThat(method(registry, "Service/Spam")).isNotNull();
	}

	@Test
	public void testRegisterTwoUnownedMethodsFromDifferentServices() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register("EchoService/Echo", Foo.class, Foo.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "EchoService/Echo")).isNotNull();
		registry.register("Service/Spam", Foo.class, Bar.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "EchoService/Echo")).isNotNull();
		assertThat(registry.descriptor(Bar.class).getFullName()).isEqualTo("Bar");
		assertThat(method(registry, "Service/Spam")).isNotNull();
	}

	@Test
	public void testRegisterTwoMixedMethods() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		registry.register("Service/Spam", Foo.class, Bar.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		assertThat(registry.descriptor(Bar.class).getFullName()).isEqualTo("Bar");
		assertThat(method(registry, "Service/Spam")).isNotNull();
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

	@Test
	public void testRegisterEmptyType() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(Void.class);
		assertThat(registry.descriptor(Void.class).getFullName()).isEqualTo("Void");
	}

	@Test
	public void testRegisterTypeTwice() throws Exception {
		DescriptorRegistry registry = new DescriptorRegistry();
		registry.register(Foo.class);
		registry.register(Foo.class);
		assertThat(registry.descriptor(Foo.class).getFullName()).isEqualTo("Foo");
		assertThat(registry.descriptor(Bar.class)).isNull();
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
