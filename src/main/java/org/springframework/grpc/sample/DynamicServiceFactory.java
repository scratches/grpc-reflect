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

import java.util.function.Function;

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
import io.grpc.protobuf.ProtoUtils;

public class DynamicServiceFactory {

	private final MessageConverter converter;
	private final DescriptorRegistry registry;

	public DynamicServiceFactory(DescriptorRegistry registry) {
		this.registry = registry;
		this.converter = new MessageConverter(registry);
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
				.build();
		ServerCallHandler<DynamicMessage, DynamicMessage> handler = new ServerCallHandler<DynamicMessage, DynamicMessage>() {
			@Override
			public Listener<DynamicMessage> startCall(ServerCall<DynamicMessage, DynamicMessage> call,
					Metadata headers) {
				call.request(2);
				return new DynamicListener<I, O>(requestType, function, call, headers);
			}
		};
		String serviceName = fullMethodName.substring(0, fullMethodName.indexOf('/'));
		ServerServiceDefinition service = ServerServiceDefinition
				.builder(ServiceDescriptor.newBuilder(serviceName)
						.setSchemaDescriptor(this.registry.method(fullMethodName))
						.addMethod(methodDescriptor)
						.build())
				.addMethod(methodDescriptor, handler)
				.build();
		return () -> service;
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
