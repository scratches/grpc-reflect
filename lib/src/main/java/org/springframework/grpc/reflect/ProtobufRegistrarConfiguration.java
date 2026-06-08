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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import com.google.protobuf.Descriptors.FileDescriptor;

public class ProtobufRegistrarConfiguration implements ImportBeanDefinitionRegistrar {

	private static int counter = 0;

	private static final String BEAN_NAME = ProtobufRegistrarConfiguration.class.getName() + ".registrar";

	@Override
	public void registerBeanDefinitions(AnnotationMetadata meta, BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(BEAN_NAME)) {
			if (!registry.containsBeanDefinition("grpcDescriptorRegistry")) {
				registry.registerBeanDefinition("grpcDescriptorRegistry",
						BeanDefinitionBuilder.genericBeanDefinition(DefaultDescriptorRegistry.class)
							.getBeanDefinition());
			}
			registry.registerBeanDefinition(BEAN_NAME,
					BeanDefinitionBuilder.genericBeanDefinition(ProtobufRegistrarPostProcessor.class)
						.getBeanDefinition());
			registry.registerBeanDefinition(BEAN_NAME + ".parser",
					BeanDefinitionBuilder.genericBeanDefinition(BinaryDescriptorParser.class).getBeanDefinition());
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

	static class ProtobufRegistrar implements DescriptorRegistrar {

		private String[] locations;

		private String base;

		private ObjectProvider<DescriptorParser> parsers;

		public ProtobufRegistrar(String base, String[] locations, ObjectProvider<DescriptorParser> parsers) {
			this.locations = locations;
			this.base = base;
			this.parsers = parsers;
		}

		@Override
		public void register(DescriptorRegistry registry) {
			FileDescriptorManager manager = new FileDescriptorManager();
			for (DescriptorParser parser : this.parsers) {
				for (FileDescriptor proto : manager.convert(parser.resolve(this.base, this.locations))) {
					registry.register(proto);
				}
			}
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
