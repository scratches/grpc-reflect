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
import java.util.function.Function;

import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;

public class DynamicServiceFactory {

	private final MessageConverter converter;
	private final DescriptorRegistry registry;

	public DynamicServiceFactory(DescriptorRegistry registry) {
		this.registry = registry;
		this.converter = new MessageConverter(registry);
	}

	public <T> BindableService service(T instance, String methodName) {
		String fullMethodName = instance.getClass().getSimpleName() + "/" + StringUtils.capitalize(methodName);
		return service(instance, methodName, fullMethodName);
	}

	public <T> BindableService service(T instance, String methodName, String fullMethodName) {
		Class<?> owner = instance.getClass();
		Method method = ReflectionUtils.findMethod(owner, methodName, (Class<?>[]) null);
		if (method == null) {
			throw new IllegalArgumentException("Method " + methodName + " not found in class " + owner.getName());
		}
		if (method.getParameterTypes().length != 1) {
			throw new IllegalArgumentException("Method must have exactly one parameter");
		}
		Class<?> requestType = method.getParameterTypes()[0];
		Class<?> responseType = method.getReturnType();
		return service(fullMethodName, requestType, responseType, request -> invoker(instance, method, request));
	}

	@SuppressWarnings("unchecked")
	private <I, O> O invoker(Object instance, Method method, I request) {
		return (O) ReflectionUtils.invokeMethod(method, instance, request);
	}

	public <I, O> BindableService service(String fullMethodName, Class<I> requestType, Class<O> responseType,
			Function<I, O> function) {
		if (function == null) {
			throw new IllegalArgumentException("Handler cannot be null");
		}
		if (this.registry.descriptor(responseType) == null) {
			this.registry.register(responseType);
		}
		if (this.registry.descriptor(requestType) == null) {
			this.registry.register(requestType);
		}
		String serviceName = fullMethodName.substring(0, fullMethodName.indexOf('/'));
		if (this.registry.file(serviceName) == null) {
			this.registry.register(fullMethodName, requestType, responseType);
		}
		Marshaller<DynamicMessage> responseMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(registry.descriptor(responseType)).build());
		Marshaller<DynamicMessage> requestMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(registry.descriptor(requestType)).build());
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
				.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(MethodDescriptor.MethodType.UNARY)
				.setFullMethodName(fullMethodName)
				.setRequestMarshaller(requestMarshaller)
				.setResponseMarshaller(responseMarshaller)
				.setSchemaDescriptor(new SimpleBaseDescriptorSupplier(registry.file(serviceName), serviceName))
				.build();
		ServerCallHandler<DynamicMessage, DynamicMessage> handler = new ServerCallHandler<DynamicMessage, DynamicMessage>() {
			@Override
			public Listener<DynamicMessage> startCall(ServerCall<DynamicMessage, DynamicMessage> call,
					Metadata headers) {
				call.request(2);
				return new DynamicListener<I, O>(requestType, function, call, headers);
			}
		};
		String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
		ServerServiceDefinition service = ServerServiceDefinition
				.builder(ServiceDescriptor.newBuilder(serviceName)
						.setSchemaDescriptor(
								new SimpleMethodDescriptor(registry.file(serviceName), serviceName, methodName))
						.addMethod(methodDescriptor)
						.build())
				.addMethod(methodDescriptor, handler)
				.build();
		return () -> service;
	}

	static class SimpleBaseDescriptorSupplier implements ProtoServiceDescriptorSupplier {

		private FileDescriptor file;
		private String serviceName;

		public SimpleBaseDescriptorSupplier(FileDescriptor file, String serviceName) {
			this.file = file;
			this.serviceName = serviceName;
		}

		@Override
		public FileDescriptor getFileDescriptor() {
			return this.file;
		}

		@Override
		public Descriptors.ServiceDescriptor getServiceDescriptor() {
			return this.file.findServiceByName(this.serviceName);
		}

	}

	static class SimpleMethodDescriptor extends SimpleBaseDescriptorSupplier
			implements ProtoMethodDescriptorSupplier {

		private final String methodName;

		SimpleMethodDescriptor(FileDescriptor file, String serviceName, String methodName) {
			super(file, serviceName);
			this.methodName = methodName;
		}

		@java.lang.Override
		public Descriptors.MethodDescriptor getMethodDescriptor() {
			return getServiceDescriptor().findMethodByName(methodName);
		}

	}

	class DynamicListener<I, O> extends ServerCall.Listener<DynamicMessage> {

		private ServerCall<DynamicMessage, DynamicMessage> call;
		private Metadata headers;
		private Class<I> requestType;
		private Function<I, O> function;

		public DynamicListener(Class<I> requestType, Function<I, O> function,
				ServerCall<DynamicMessage, DynamicMessage> call, Metadata headers) {
			this.requestType = requestType;
			this.function = function;
			this.call = call;
			this.headers = headers;
		}

		@Override
		public void onCancel() {
		}

		@Override
		public void onComplete() {
		}

		@Override
		public void onHalfClose() {
		}

		@Override
		public void onMessage(DynamicMessage message) {
			I input = converter.convert(message, requestType);
			O output = function.apply(input);
			this.call.sendMessage((DynamicMessage) converter.convert(output));
			this.call.close(Status.OK, new Metadata());
		}

		@Override
		public void onReady() {
			call.sendHeaders(this.headers);
		}

	}

}
