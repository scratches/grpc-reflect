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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.grpc.sample.proto.HelloWorldProto;
import org.springframework.util.StringUtils;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;

public class DescriptorRegistryTests {

	private DescriptorProtoExtractor extractor = DescriptorProtoExtractor.DEFAULT_INSTANCE;

	private DescriptorCatalog catalog = new DescriptorCatalog();

	private DescriptorRegistry registry = new DescriptorRegistry(catalog);

	private ReflectionFileDescriptorProvider reflection = new ReflectionFileDescriptorProvider(extractor);

	@Test
	public void testRegisterGeneratedTypes() throws Exception {
		registry.register(HelloWorldProto.getDescriptor().findServiceByName("Simple").findMethodByName("SayHello"),
				Foo.class, Foo.class);
		assertThat(registry.input("Simple/SayHello").descriptor().getFullName()).isEqualTo("HelloRequest");
		assertThat(registry.output("Simple/SayHello").descriptor().getFullName()).isEqualTo("HelloReply");
		assertThat(method(registry, "Simple/SayHello")).isNotNull();
	}

	@Test
	public void testRegisterMethod() throws Exception {
		register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		assertThat(registry.output("DescriptorRegistryTests/Echo").descriptor().getFullName()).isEqualTo("Foo");
	}

	@Test
	public void testRegisterTwoMethods() throws Exception {
		register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		assertThat(registry.output("DescriptorRegistryTests/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		register(DescriptorRegistryTests.class.getMethod("translate", Foo.class));
		assertThat(registry.input("DescriptorRegistryTests/Translate").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(registry.output("DescriptorRegistryTests/Translate").descriptor().getFullName()).isEqualTo("Bar");
	}

	@Test
	public void testRegisterUnownedMethod() throws Exception {
		reflection.unary("Service/Spam", Foo.class, Bar.class);
		registry.register(method(reflection, "Service/Spam"), Foo.class, Bar.class);
		assertThat(method(registry, "Service/Spam")).isNotNull();
		assertThat(registry.input("Service/Spam").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(registry.output("Service/Spam").descriptor().getFullName()).isEqualTo("Bar");
	}

	@Test
	public void testRegisterTwoUnownedMethods() throws Exception {
		reflection.unary("Service/Echo", Foo.class, Foo.class);
		reflection.unary("Service/Spam", Foo.class, Bar.class);
		assertThat(method(reflection, "Service/Echo")).isNotNull();
		registry.register(method(reflection, "Service/Echo"), Foo.class, Foo.class);
		assertThat(registry.input("Service/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(method(reflection, "Service/Spam")).isNotNull();
		registry.register(method(reflection, "Service/Spam"), Foo.class, Bar.class);
		assertThat(registry.input("Service/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(registry.output("Service/Spam").descriptor().getFullName()).isEqualTo("Bar");
	}

	@Test
	public void testRegisterTwoUnownedMethodsFromDifferentServices() throws Exception {
		reflection.unary("EchoService/Echo", Foo.class, Foo.class);
		reflection.unary("Service/Spam", Foo.class, Bar.class);
		assertThat(method(reflection, "EchoService/Echo")).isNotNull();
		registry.register(method(reflection, "EchoService/Echo"), Foo.class, Foo.class);
		assertThat(registry.input("EchoService/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(method(reflection, "Service/Spam")).isNotNull();
		registry.register(method(reflection, "Service/Spam"), Foo.class, Bar.class);
		assertThat(registry.input("EchoService/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(registry.output("Service/Spam").descriptor().getFullName()).isEqualTo("Bar");
	}

	@Test
	public void testRegisterTwoMixedMethods() throws Exception {
		register(DescriptorRegistryTests.class.getMethod("echo", Foo.class));
		reflection.unary("Service/Spam", Foo.class, Bar.class);
		assertThat(method(reflection, "Service/Spam")).isNotNull();
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		registry.register(method(reflection, "Service/Spam"), Foo.class, Bar.class);
		assertThat(registry.input("DescriptorRegistryTests/Echo").descriptor().getFullName()).isEqualTo("Foo");
		assertThat(method(registry, "DescriptorRegistryTests/Echo")).isNotNull();
		assertThat(registry.output("Service/Spam").descriptor().getFullName()).isEqualTo("Bar");
	}

	private void register(Method method) {
		Class<?> owner = method.getDeclaringClass();
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();

		if (Publisher.class.isAssignableFrom(outputType)) {
			Type genericOutputType = method.getGenericReturnType();
			if (genericOutputType instanceof ParameterizedType parameterized) {
				outputType = (Class<?>) parameterized.getActualTypeArguments()[0];
			}
			if (Publisher.class.isAssignableFrom(inputType)) {
				Type genericInputType = method.getGenericParameterTypes()[0];
				if (genericInputType instanceof ParameterizedType parameterized) {
					inputType = (Class<?>) parameterized.getActualTypeArguments()[0];
				}
				reflection.bidi(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType,
						outputType);
			}
			else {
				reflection.stream(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType,
						outputType);
			}
		}
		else {
			reflection.unary(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType,
					outputType);
		}
		registry.register(
				reflection.file(method.getDeclaringClass().getSimpleName())
					.findServiceByName(method.getDeclaringClass().getSimpleName())
					.findMethodByName(StringUtils.capitalize(method.getName())),
				method.getParameterTypes()[0], method.getReturnType());
	}

	private MethodDescriptor method(FileDescriptorProvider registry, String fullMethodName) {
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
