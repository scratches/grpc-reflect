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
package org.springframework.grpc.sample;

import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.grpc.sample.DynamicServiceFactory.BindableServiceInstanceBuilder;

import io.grpc.BindableService;

public class GrpcMappingRegistrar implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(GrpcMappingPostProcessor.GRPC_MAPPING_BEAN_NAME)) {
			registry.registerBeanDefinition(GrpcMappingPostProcessor.GRPC_MAPPING_BEAN_NAME,
					BeanDefinitionBuilder.genericBeanDefinition(GrpcMappingPostProcessor.class).getBeanDefinition());
		}
	}

	static class GrpcMappingPostProcessor implements BeanFactoryPostProcessor {
		static String GRPC_MAPPING_BEAN_NAME = "grpcMappingPostProcessor";

		@Override
		public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
			String factoryName = beanFactory.getBeanNamesForType(DynamicServiceFactory.class)[0];
			for (String name : beanFactory.getBeanNamesForAnnotation(GrpcController.class)) {
				BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(BindableService.class);
				builder.addConstructorArgReference(factoryName);
				builder.addConstructorArgReference(name);
				builder.setFactoryMethodOnBean("create", GRPC_MAPPING_BEAN_NAME);
				((DefaultListableBeanFactory)beanFactory).registerBeanDefinition(name + "_service", builder.getBeanDefinition());
			}
		}

		public BindableService create(DynamicServiceFactory dynamic, Object instance) {
			BindableServiceInstanceBuilder service = dynamic.service(instance);
			for (Method method : instance.getClass().getDeclaredMethods()) {
				if (method.isAnnotationPresent(GrpcMapping.class)) {
					// TODO: use the mapping GrpcMapping mapping = method.getAnnotation(GrpcMapping.class);
					service.method(method.getName());
				}
			}
			return service.build();
		}

	}

}
