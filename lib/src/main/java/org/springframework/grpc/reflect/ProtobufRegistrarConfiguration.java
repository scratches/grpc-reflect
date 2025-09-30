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

import java.nio.file.Path;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.grpc.parser.FileDescriptorProtoParser;

import com.google.protobuf.Descriptors.FileDescriptor;

@Configuration(proxyBeanMethods = false)
public class ProtobufRegistrarConfiguration implements ImportAware, DescriptorRegistrar {

	private String[] locations;

	private FileDescriptorProtoParser parser = new FileDescriptorProtoParser();

	private FileDescriptorManager manager = new FileDescriptorManager();

	@Override
	public void register(DescriptorRegistry registry) {
		if (this.locations != null) {
			Path[] paths = new Path[this.locations.length];
			for (int i = 0; i < this.locations.length; i++) {
				paths[i] = Path.of(this.locations[i].trim());
			}
			for (FileDescriptor proto : manager.convert(this.parser.resolve(paths))) {
				registry.register(proto);
			}
		}
	}

	@Bean
	public DefaultDescriptorRegistry grpcDescriptorRegistry() {
		return new DefaultDescriptorRegistry();
	}

	@Bean
	public static BeanFactoryPostProcessor descriptorRegistrarPostProcessor(
			ObjectProvider<DescriptorRegistrar> registrars, DescriptorRegistry registry) {
		return new BeanFactoryPostProcessor() {
			@Override
			public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
					throws BeansException {
				registrars.orderedStream().forEach(registrar -> registrar.register(registry));
			}
		};
	}

	@Override
	public void setImportMetadata(AnnotationMetadata meta) {
		ImportProtobuf annotation = ImportProtobuf.class
				.cast(meta.getAnnotations().get(ImportProtobuf.class.getName()).synthesize());
		String[] locations = annotation.locations();
		if (locations.length == 0) {
			locations = annotation.value();
		}
		this.locations = locations;
	}

}
