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
import org.springframework.grpc.sample.proto.HelloWorldProto;

import com.google.protobuf.Any;

import io.grpc.reflection.v1.ServerReflectionProto;

public class DescriptorCatalogTests {

	private DescriptorCatalog catalog = new DescriptorCatalog();

	@Test
	public void testRegisterFile() throws Exception {
		catalog.register(HelloWorldProto.getDescriptor().getFile());
		assertThat(catalog.type("HelloRequest")).isNotNull();
		assertThat(catalog.service("Simple")).isNotNull();
	}

	@Test
	public void testRegisterStandardTypes() throws Exception {
		catalog.register(Any.getDescriptor().getFile());
		assertThat(catalog.type("google.protobuf.Any")).isNotNull();
	}

	@Test
	public void testRegisterService() throws Exception {
		catalog.register(HelloWorldProto.getDescriptor().getFile().findServiceByName("Simple"));
		assertThat(catalog.type("HelloRequest")).isNotNull();
		assertThat(catalog.service("Simple")).isNotNull();
	}

	@Test
	public void testRegisterStandardService() throws Exception {
		catalog.register(ServerReflectionProto.getDescriptor().getFile());
		assertThat(catalog.type("grpc.reflection.v1.ServerReflectionRequest")).isNotNull();
		assertThat(catalog.service("grpc.reflection.v1.ServerReflection")).isNotNull();
	}

}
