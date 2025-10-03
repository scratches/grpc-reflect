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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.grpc.parser.FileDescriptorProtoParser;

import com.google.protobuf.Descriptors.FileDescriptor;

public class ProtobufRegistrarConfiguration implements ImportBeanDefinitionRegistrar {

	private static int counter = 0;

	private static final String BEAN_NAME = ProtobufRegistrarConfiguration.class.getName() + ".registrar";

	@Override
	public void registerBeanDefinitions(AnnotationMetadata meta, BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(BEAN_NAME)) {
			registry.registerBeanDefinition("grpcDescriptorRegistry",
					BeanDefinitionBuilder.genericBeanDefinition(DefaultDescriptorRegistry.class).getBeanDefinition());
			registry.registerBeanDefinition(BEAN_NAME,
					BeanDefinitionBuilder.genericBeanDefinition(ProtobufRegistrarPostProcessor.class)
							.getBeanDefinition());
		}
		ImportProtobuf annotation = ImportProtobuf.class
				.cast(meta.getAnnotations().get(ImportProtobuf.class.getName()).synthesize());
		String[] locations = annotation.locations();
		if (locations.length == 0) {
			locations = annotation.value();
		}
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ProtobufRegistrar.class);
		builder.addConstructorArgValue(locations);
		registry.registerBeanDefinition(BEAN_NAME + (counter++), builder.getBeanDefinition());
	}

	static class ProtobufRegistrar implements DescriptorRegistrar, ResourceLoaderAware {

		private String[] locations;

		private PathMatchingResourcePatternResolver resourceLoader;

		public ProtobufRegistrar(String[] locations) {
			this.locations = locations;
		}

		@Override
		public void register(DescriptorRegistry registry) {
			FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
			FileDescriptorManager manager = new FileDescriptorManager();
			if (this.locations != null) {
				List<Path> paths = new ArrayList<>();
				for (String location : this.locations) {
					try {
						Resource[] resources = resourceLoader.getResources(location);
						for (Resource resource : resources) {
							if (resource.exists()) {
								String url = location;
								if (url.contains(":")) {
									url = url.substring(url.indexOf(":") + 1);
								}
								if (url.startsWith("//")) {
									url = url.substring(2);
								}
								paths.add(Path.of(url));
							}
						}
					} catch (IOException e) {
						throw new IllegalStateException("Failed to find resources for location: " + location, e);
					}
				}
				for (FileDescriptor proto : manager.convert(parser.resolve(paths.toArray(new Path[0])))) {
					registry.register(proto);
				}
			}
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = new PathMatchingResourcePatternResolver(resourceLoader);
		}

	}

	static class ProtobufRegistrarPostProcessor implements BeanFactoryPostProcessor, ApplicationContextAware {

		private ApplicationContext context;

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.context = applicationContext;
		}

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
				throws BeansException {
			ObjectProvider<DescriptorRegistrar> registrars = context.getBeanProvider(DescriptorRegistrar.class);
			DescriptorRegistry registry = context.getBean(DescriptorRegistry.class);
			registrars.orderedStream().forEach(registrar -> registrar.register(registry));
		}
	}

}
