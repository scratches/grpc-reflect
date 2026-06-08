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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;

public class ProtobufRegistrationTests {

	@Test
	public void testFileSystemNoScheme() {
		// The file is not on the classpath, so it should fail to load without the "file:"
		// scheme prefix
		assertThrows(BeanCreationException.class, () -> {
			ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(
					FileSystemExampleNoScheme.class, Parsers.class);
			DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
			assertThat(file(registry, "Simple")).isNull();
			context.close();
		});
	}

	@Test
	public void testFileSystem() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(FileSystemExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple")).isNotNull();
		assertThat(registry.type("HelloRequest")).isNotNull();
		context.close();
	}

	@Test
	public void testClasspath() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(ClasspathExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple")).isNotNull();
		assertThat(registry.type("HelloRequest")).isNotNull();
		context.close();
	}

	@Test
	public void testBase() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(BaseExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple").getName()).isEqualTo("simple.proto");
		context.close();
	}

	@Test
	public void testBaseUrl() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(BaseUrlExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple").getName()).isEqualTo("simple.proto");
		context.close();
	}

	@Test
	public void testMultiple() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(MultipleExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple")).isNotNull();
		assertThat(file(registry, "Foo")).isNotNull();
		context.close();
	}

	@Test
	public void testMultipleImports() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(OtherExample.class,
				FileSystemExample.class, Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple")).isNotNull();
		assertThat(file(registry, "Foo")).isNotNull();
		context.close();
	}

	@Test
	public void testPatternImports() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(PatternExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple").getName()).isEqualTo("proto/simple.proto");
		assertThat(file(registry, "Foo").getName()).isEqualTo("proto/bar.proto");
		context.close();
	}

	@Test
	public void testBasePatternImports() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(BasePatternExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "Simple").getName()).isEqualTo("simple.proto");
		assertThat(file(registry, "Foo").getName()).isEqualTo("bar.proto");
		context.close();
	}

	@Test
	public void testJarFileImport() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(JarFileExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "grpc.reflection.v1.ServerReflection").getName())
			.isEqualTo("grpc/reflection/v1/reflection.proto");
		assertThat(registry.service("grpc.reflection.v1.ServerReflection").getFile().getName()).isNotNull();
		assertThat(registry.type("grpc.reflection.v1.ServerReflectionRequest")).isNotNull();
		context.close();
	}

	@Test
	public void testJarFileBaseImport() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(JarFileBaseExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "grpc.reflection.v1.ServerReflection").getName())
			.isEqualTo("grpc/reflection/v1/reflection.proto");
		assertThat(registry.service("grpc.reflection.v1.ServerReflection").getFile().getName()).isNotNull();
		assertThat(registry.type("grpc.reflection.v1.ServerReflectionRequest")).isNotNull();
		context.close();
	}

	@Test
	public void testClasspathPrefix() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(ClasspathPrefixExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(file(registry, "grpc.reflection.v1.ServerReflection").getName())
			.isEqualTo("grpc/reflection/v1/reflection.proto");
		context.close();
	}

	@Test
	public void testBinary() {
		ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(BinaryExample.class,
				Parsers.class);
		DefaultDescriptorRegistry registry = context.getBean(DefaultDescriptorRegistry.class);
		assertThat(registry.type("EchoRequest").getFile().getName()).isEqualTo("foo.proto");
		context.close();
	}

	private FileDescriptor file(DefaultDescriptorRegistry registry, String serviceName) {
		ServiceDescriptor service = registry.service(serviceName);
		return service == null ? null : service.getFile();
	}

	@Configuration(proxyBeanMethods = false)
	static class Parsers {

		@Bean
		ProtoDescriptorParser protoParser() {
			return new ProtoDescriptorParser();
		}

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
	static class OtherExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf("proto/*.proto")
	static class PatternExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf({ "proto/bar.proto", "file:src/test/proto/hello.proto" })
	static class MultipleExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "simple.proto", base = "proto")
	static class BaseExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "simple.proto", base = "classpath:proto")
	static class BaseUrlExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "*.proto", base = "proto")
	static class BasePatternExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "grpc/reflection/v1/reflection.proto")
	static class JarFileExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(base = "classpath:/", locations = "grpc/reflection/v1/reflection.proto")
	static class JarFileBaseExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "classpath:/grpc/reflection/v1/reflection.proto")
	static class ClasspathPrefixExample {

	}

	@Configuration(proxyBeanMethods = false)
	@ImportProtobuf(locations = "classpath:/binary/multi.pb")
	static class BinaryExample {

	}

}
