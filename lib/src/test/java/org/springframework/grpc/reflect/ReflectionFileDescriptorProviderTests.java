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

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;

public class ReflectionFileDescriptorProviderTests {

	private DescriptorProtoExtractor extractor = DescriptorProtoExtractor.DEFAULT_INSTANCE;
	private ReflectionFileDescriptorProvider registry = new ReflectionFileDescriptorProvider(extractor);

	@Test
	public void testRegisterMethod() throws Exception {
		register(registry, ReflectionFileDescriptorProviderTests.class.getMethod("echo", Foo.class));
		assertThat(method(registry, "ReflectionFileDescriptorProviderTests/Echo")).isNotNull();
	}

	@Test
	public void testRegisterTwoMethods() throws Exception {
		register(registry, ReflectionFileDescriptorProviderTests.class.getMethod("echo", Foo.class));
		assertThat(method(registry, "ReflectionFileDescriptorProviderTests/Echo")).isNotNull();
		register(registry, ReflectionFileDescriptorProviderTests.class.getMethod("translate", Foo.class));
		assertThat(method(registry, "ReflectionFileDescriptorProviderTests/Echo")).isNotNull();
		assertThat(method(registry, "ReflectionFileDescriptorProviderTests/Translate")).isNotNull();
	}

	private void register(ReflectionFileDescriptorProvider registry, Method method) {
		Class<?> owner = method.getDeclaringClass();
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();
				registry.unary(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType,
				outputType);
	}

	private MethodDescriptor method(ReflectionFileDescriptorProvider registry, String fullMethodName) {
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

	public static class Bar {
	}

	public Bar translate(Foo foo) {
		return new Bar();
	}

	public Foo echo(Foo foo) {
		return foo;
	}
}
