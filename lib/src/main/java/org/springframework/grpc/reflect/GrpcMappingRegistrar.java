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

import java.lang.reflect.Method;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.grpc.reflect.DynamicServiceFactory.BindableServiceInstanceBuilder;

import io.grpc.BindableService;

/**
 * Bean definition registrar for gRPC mapping functionality.
 * <p>
 * This registrar implements {@link ImportBeanDefinitionRegistrar} to
 * programmatically register beans required for gRPC method mapping and 
 * reflection capabilities in the Spring application context.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcMappingRegistrar implements ImportBeanDefinitionRegistrar {

	static String BINDABLE_SERVICE_FACTORY = "grpcBindableServiceFactory";

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		if (!registry.containsBeanDefinition(BINDABLE_SERVICE_FACTORY)) {
			registry.registerBeanDefinition(BINDABLE_SERVICE_FACTORY,
					BeanDefinitionBuilder.genericBeanDefinition(BindableServiceFactory.class).getBeanDefinition());
			for (String name : registry.getBeanDefinitionNames()) {
				BeanDefinition definition = registry.getBeanDefinition(name);
				if (definition.getBeanClassName() != null) {
					try {
						Class<?> clazz = Class.forName(definition.getBeanClassName());
						if (clazz.isAnnotationPresent(GrpcController.class)) {
							BeanDefinitionBuilder builder = BeanDefinitionBuilder
									.genericBeanDefinition(BindableService.class);
							builder.addConstructorArgReference(name);
							builder.setFactoryMethodOnBean("create", BINDABLE_SERVICE_FACTORY);
							registry.registerBeanDefinition(name + "_service", builder.getBeanDefinition());
						}
					} catch (ClassNotFoundException e) {
						// Ignore
					}
				}
			}
		}
	}

	static class BindableServiceFactory {

		private ObjectProvider<DynamicServiceFactory> factory;

		BindableServiceFactory(ObjectProvider<DynamicServiceFactory> factory) {
			this.factory = factory;
		}

		public BindableService create(Object instance) {
			String serviceName = instance.getClass().getAnnotation(GrpcController.class).value();
			if (serviceName.isEmpty()) {
				serviceName = instance.getClass().getSimpleName();
			}
			BindableServiceInstanceBuilder service = factory.getObject().service(serviceName, instance);
			for (Method method : instance.getClass().getDeclaredMethods()) {
				if (method.isAnnotationPresent(GrpcMapping.class)) {
					GrpcMapping mapping = method.getAnnotation(GrpcMapping.class);
					if (mapping.value().isEmpty()) {
						service.method(method);
					} else {
						service.method(method, mapping.value());
					}
				}
			}
			return service.build();
		}

	}

}
