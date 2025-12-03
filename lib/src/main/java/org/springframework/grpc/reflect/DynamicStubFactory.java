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
import java.lang.reflect.ParameterizedType;
import java.util.function.Supplier;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.grpc.client.StubFactory;
import org.springframework.util.StringUtils;

import io.grpc.ManagedChannel;
import reactor.core.publisher.Flux;

/**
 * Factory for creating dynamic gRPC client stubs.
 * <p>
 * This factory implements {@link StubFactory} to provide dynamic stub creation
 * capabilities, allowing clients to interact with gRPC services without requiring
 * compile-time generated stub classes.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DynamicStubFactory implements StubFactory<Object> {

	private final DefaultDescriptorRegistry descriptorRegistry;

	public DynamicStubFactory(DefaultDescriptorRegistry descriptorRegistry) {
		this.descriptorRegistry = descriptorRegistry;
	}

	public static boolean supports(Class<?> type) {
		return type.isInterface() && type.isAnnotationPresent(GrpcService.class);
	}

	@Override
	public Object create(Supplier<ManagedChannel> channel, Class<?> type) {
		DynamicStub stub = new DynamicStub(descriptorRegistry, channel.get());
		ProxyFactory proxyFactory = new ProxyFactory(stub);
		proxyFactory.setTargetClass(type);
		if (type.isInterface()) {
			proxyFactory.setProxyTargetClass(false);
		}
		proxyFactory.addAdvice(new DynamicStubMethodInterceptor(stub));
		return proxyFactory.getProxy();
	}

	static class DynamicStubMethodInterceptor implements MethodInterceptor {

		private final DynamicStub stub;

		public DynamicStubMethodInterceptor(DynamicStub stub) {
			this.stub = stub;
		}

		@Override
		public Object invoke(MethodInvocation invocation) throws Throwable {
			if (invocation.getMethod().getDeclaringClass() == Object.class) {
				return invocation.getMethod().invoke(this.stub, invocation.getArguments());
			}
			GrpcService client = AnnotationUtils.findAnnotation(invocation.getMethod().getDeclaringClass(),
					GrpcService.class);
			GrpcMapping mapping = AnnotationUtils.findAnnotation(invocation.getMethod(), GrpcMapping.class);
			String methodName = service(client, invocation.getMethod().getDeclaringClass()) + "/"
					+ method(mapping, invocation.getMethod());
			Object[] arguments = invocation.getArguments();
			if (Publisher.class.isAssignableFrom(invocation.getMethod().getReturnType())) {
				Class<?> responseType = (Class<?>) ((ParameterizedType) (invocation.getMethod().getGenericReturnType()))
					.getActualTypeArguments()[0];
				if (Publisher.class.isAssignableFrom(invocation.getMethod().getParameterTypes()[0])) {
					@SuppressWarnings({ "rawtypes", "unchecked" })
					Flux<?> result = this.stub.bidi(methodName, (Publisher) arguments[0], responseType);
					return result;
				}
				else {
					return this.stub.stream(methodName, arguments[0], responseType);
				}
			}
			return this.stub.unary(methodName, arguments[0], invocation.getMethod().getReturnType());
		}

		private String method(GrpcMapping mapping, Method method) {
			String methodName = mapping == null ? "" : mapping.path();
			if (StringUtils.hasText(methodName)) {
				return methodName;
			}
			return StringUtils.capitalize(method.getName());
		}

		private String service(GrpcService client, Class<?> type) {
			String service = client.service();
			return service.isEmpty() ? type.getSimpleName() : service;
		}

	}

}
