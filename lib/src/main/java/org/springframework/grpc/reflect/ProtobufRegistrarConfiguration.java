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
import org.springframework.beans.factory.config.BeanPostProcessor;
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
			if (!registry.containsBeanDefinition("grpcDescriptorRegistry")) {
				registry.registerBeanDefinition("grpcDescriptorRegistry",
						BeanDefinitionBuilder.genericBeanDefinition(DefaultDescriptorRegistry.class).getBeanDefinition());
			}
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
		String base = annotation.base();
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ProtobufRegistrar.class);
		builder.addConstructorArgValue(base);
		builder.addConstructorArgValue(locations);
		registry.registerBeanDefinition(BEAN_NAME + (counter++), builder.getBeanDefinition());
	}

	static class ProtobufRegistrar implements DescriptorRegistrar, ResourceLoaderAware {

		private String[] locations;

		private String base;

		private PathMatchingResourcePatternResolver resourceLoader;

		public ProtobufRegistrar(String base, String[] locations) {
			this.locations = locations;
			this.base = base;
		}

		@Override
		public void register(DescriptorRegistry registry) {
			FileDescriptorProtoParser parser = new FileDescriptorProtoParser(Path.of(base));
			FileDescriptorManager manager = new FileDescriptorManager();
			if (this.locations != null) {
				List<Path> paths = new ArrayList<>();
				for (String location : this.locations) {
					boolean hasBase = false;
					if (base.length() > 0) {
						if (!location.contains(":") && !location.startsWith("/")) {
							location = base + (base.endsWith("/") ? "" : "/") + location;
							hasBase = true;
						}
					}
					String rootDir = determineRootDir(location);
					try {
						Resource[] resources = resourceLoader.getResources(location);
						for (Resource resource : resources) {
							if (resource.exists()) {
								String url = resource.getURL().getPath();
								url = url.substring(url.lastIndexOf(rootDir));
								if (hasBase && url.startsWith(base)) {
									url = url.substring(base.length());
									if (url.startsWith("/")) {
										url = url.substring(1);
									}
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

		private String determineRootDir(String location) {
			if (location.contains(":")) {
				location = location.substring(location.indexOf(':') + 1);
			}
			if (!this.resourceLoader.getPathMatcher().isPattern(location)) {
				return location;
			}
			int rootDirEnd = location.length();
			while (rootDirEnd > 0
					&& this.resourceLoader.getPathMatcher().isPattern(location.substring(0, rootDirEnd))) {
				rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
			}
			if (rootDirEnd == 0) {
				rootDirEnd = 0;
			}
			return location.substring(0, rootDirEnd);
		}

	}

	static class ProtobufRegistrarPostProcessor implements BeanPostProcessor, ApplicationContextAware {

		private ApplicationContext context;

		@Override
		public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
			this.context = applicationContext;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof DescriptorRegistry registry) {
				ObjectProvider<DescriptorRegistrar> registrars = context.getBeanProvider(DescriptorRegistrar.class);
				registrars.orderedStream().forEach(registrar -> registrar.register(registry));
			}
			return bean;
		}
	}

}
