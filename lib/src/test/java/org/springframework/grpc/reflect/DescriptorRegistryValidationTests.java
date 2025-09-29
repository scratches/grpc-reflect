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

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.grpc.sample.proto.HelloWorldProto;
import org.springframework.util.StringUtils;

public class DescriptorRegistryValidationTests {

	@Test
	public void testValidateUnregistered() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		assertThrows(IllegalStateException.class,
				() -> registry.validate("DescriptorRegistryValidationTests/Echo", Foo.class, Foo.class));
	}

	@Test
	public void testValidateReflectedTypes() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		register(registry, DescriptorRegistryValidationTests.class.getMethod("echo", Foo.class));
		registry.validate("DescriptorRegistryValidationTests/Echo", Foo.class, Foo.class);
	}

	@Test
	public void testValidateGeneratedTypes() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		registry.register(HelloWorldProto.getDescriptor().findServiceByName("Simple"));
		registry.input("Simple/SayHello", Foo.class,
				HelloWorldProto.getDescriptor().findMessageTypeByName("HelloRequest"));
		registry.validate("Simple/SayHello", Foo.class, Response.class);
	}

	@Test
	public void testValidateBadRequest() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		registry.register(HelloWorldProto.getDescriptor().findServiceByName("Simple"));
		registry.input("Simple/SayHello", Foo.class,
				HelloWorldProto.getDescriptor().findMessageTypeByName("HelloRequest"));
		registry.validate("Simple/SayHello", Foo.class, Response.class);
		assertThrows(IllegalArgumentException.class,
				() -> registry.validate("Simple/SayHello", Response.class, Response.class));
	}

	@Test
	public void testValidateBadProperty() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		registry.register(HelloWorldProto.getDescriptor().findServiceByName("Simple"));
		registry.input("SimpleSayHello", Foo.class,
				HelloWorldProto.getDescriptor().findMessageTypeByName("HelloRequest"));
		registry.validate("Simple/SayHello", Foo.class, Response.class);
		String message = assertThrows(IllegalArgumentException.class,
				() -> registry.validate("Simple/SayHello", Wrong.class, Response.class))
			.getMessage();
		assertThat(message).contains("Field 'name'");
	}

	@Test
	public void testValidateBadResponse() throws Exception {
		DefaultDescriptorRegistry registry = new DefaultDescriptorRegistry();
		registry.register(HelloWorldProto.getDescriptor().findServiceByName("Simple"));
		registry.input("Simple/SayHello", Foo.class,
				HelloWorldProto.getDescriptor().findMessageTypeByName("HelloRequest"));
		registry.validate("Simple/SayHello", Foo.class, Response.class);
		assertThrows(IllegalArgumentException.class, () -> registry.validate("Simple/SayHello", Foo.class, Foo.class));
	}

	private void register(DefaultDescriptorRegistry registry, Method method) {
		Class<?> owner = method.getDeclaringClass();
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();
		registry.unary(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType, outputType);
	}

	public static class Response {

		private String message;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

	}

	public static class Wrong {

		private int name;

		public int getName() {
			return name;
		}

		public void setName(int name) {
			this.name = name;
		}

	}

	public Foo echo(Foo foo) {
		return foo;
	}

}
