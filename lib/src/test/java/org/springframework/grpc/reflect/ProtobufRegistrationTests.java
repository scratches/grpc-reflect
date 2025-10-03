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

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

public class ProtobufRegistrationTests {

	@Test
	public void testFileSystemNoScheme() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
				FileSystemExampleNoScheme.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.file("Simple")).isNull();
		context.close();
	}

	@Test
	public void testFileSystem() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(FileSystemExample.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.file("Simple")).isNotNull();
		context.close();
	}

	@Test
	public void testClasspath() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(ClasspathExample.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.file("Simple")).isNotNull();
		context.close();
	}

	@Test
	public void testMultiple() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(MultipleExample.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.file("Simple")).isNotNull();
		assertThat(registry.file("Foo")).isNotNull();
		context.close();
	}

	@Test
	public void testMultipleImports() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(NoServiceExample.class,
				FileSystemExample.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.file("Simple")).isNotNull();
		assertThat(registry.file("Foo")).isNotNull();
		context.close();
	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf("file:src/test/proto/hello.proto")
	static class FileSystemExample {
	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf("src/test/proto/hello.proto")
	static class FileSystemExampleNoScheme {
	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf("proto/simple.proto")
	static class ClasspathExample {
	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf("proto/bar.proto")
	static class NoServiceExample {
	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf({"proto/bar.proto", "file:src/test/proto/hello.proto"})
	static class MultipleExample {
	}
}
