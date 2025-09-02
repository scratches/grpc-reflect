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
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.ServiceDescriptor.Builder;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public class DynamicServiceFactory {

	private final MessageConverter converter;

	private final DescriptorRegistrar registry;

	public DynamicServiceFactory(DescriptorRegistrar registry) {
		this.registry = registry;
		this.converter = new MessageConverter();
	}

	public <T> BindableServiceBuilder service(String serviceName) {
		return new BindableServiceBuilder(serviceName, this.registry, this.converter);
	}

	public <T> BindableServiceInstanceBuilder service(T instance) {
		return new BindableServiceInstanceBuilder(instance, instance.getClass().getSimpleName(), this.registry,
				this.converter);
	}

	public <T> BindableServiceInstanceBuilder service(String serviceName, T instance) {
		return new BindableServiceInstanceBuilder(instance, serviceName, this.registry, this.converter);
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

	static class SimpleMethodDescriptor extends SimpleBaseDescriptorSupplier implements ProtoMethodDescriptorSupplier {

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

	public static class BindableServiceInstanceBuilder {

		private BindableServiceBuilder builder;

		private Object instance;

		private <T> BindableServiceInstanceBuilder(T instance, String serviceName, DescriptorRegistrar registry,
				MessageConverter converter) {
			this.instance = instance;
			this.builder = new BindableServiceBuilder(serviceName, registry, converter);
		}

		public BindableServiceInstanceBuilder method(String methodName) {
			Class<?> owner = instance.getClass();
			Method method = ReflectionUtils.findMethod(owner, methodName, (Class<?>[]) null);
			if (method == null) {
				throw new IllegalArgumentException("Method " + methodName + " not found in class " + owner.getName());
			}
			if (method.getParameterTypes().length != 1) {
				throw new IllegalArgumentException(
						"Method " + methodName + " must have exactly one parameter in class " + owner.getName());
			}
			return method(method);
		}

		public BindableServiceInstanceBuilder method(Method method) {
			return method(method, StringUtils.capitalize(method.getName()));
		}

		public BindableServiceInstanceBuilder method(Method method, String methodName) {
			Class<?> requestType = method.getParameterTypes()[0];
			Type genericRequestType = method.getGenericParameterTypes()[0];
			Class<?> responseType = method.getReturnType();
			Type genericResponseType = method.getGenericReturnType();
			if (requestType.equals(genericRequestType) && responseType.equals(genericResponseType)) {
				this.builder.unary(methodName, requestType, responseType,
						request -> invoker(instance, method, request));
			}
			else if (Publisher.class.isAssignableFrom(responseType)) {
				responseType = (Class<?>) ((ParameterizedType) genericResponseType).getActualTypeArguments()[0];
				if (Publisher.class.isAssignableFrom(requestType)) {
					requestType = (Class<?>) ((ParameterizedType) genericRequestType).getActualTypeArguments()[0];
					this.builder.bidi(methodName, requestType, responseType,
							request -> invoker(instance, method, request));
				}
				else {
					this.builder.stream(methodName, requestType, responseType,
							request -> invoker(instance, method, request));
				}
			}
			else {
				throw new IllegalStateException(
						"Unsupported request and response types [" + requestType + ", " + responseType + "]");
			}
			return this;
		}

		@SuppressWarnings("unchecked")
		private <I, O> O invoker(Object instance, Method method, I request) {
			ReflectionUtils.makeAccessible(method);
			return (O) ReflectionUtils.invokeMethod(method, instance, request);
		}

		public BindableService build() {
			return this.builder.build();
		}

	}

	public static class BindableServiceBuilder {

		private String serviceName;

		private FileDescriptorProvider registry;

		private DescriptorRegistrar registrar;

		private MessageConverter converter;

		private Map<String, ServerCallHandler<DynamicMessage, DynamicMessage>> handlers = new HashMap<>();

		private Map<String, MethodDescriptor<DynamicMessage, DynamicMessage>> descriptors = new HashMap<>();

		private BindableServiceBuilder(String serviceName, DescriptorRegistrar registry, MessageConverter converter) {
			this.serviceName = serviceName;
			this.registry = registry;
			this.registrar = registry;
			this.converter = converter;
		}

		public <I, O> BindableServiceBuilder unary(String methodName, Class<I> requestType, Class<O> responseType,
				Function<I, O> function) {
			return method(methodName, requestType, responseType, function, MethodDescriptor.MethodType.UNARY);
		}

		public <I, O> BindableServiceBuilder stream(String methodName, Class<I> requestType, Class<O> responseType,
				Function<I, Publisher<O>> function) {
			return method(methodName, requestType, responseType, function,
					MethodDescriptor.MethodType.SERVER_STREAMING);
		}

		public <I, O> BindableServiceBuilder bidi(String methodName, Class<I> requestType, Class<O> responseType,
				Function<Publisher<I>, Publisher<O>> function) {
			return method(methodName, requestType, responseType, function, MethodDescriptor.MethodType.BIDI_STREAMING);
		}

		private <I, O> BindableServiceBuilder method(String methodName, Class<I> requestType, Class<O> responseType,
				Function<?, ?> function, MethodType methodType) {
			String fullMethodName = serviceName + "/" + methodName;
			if (this.registry.file(serviceName) == null
					|| this.registry.file(serviceName).findServiceByName(serviceName) == null
					|| this.registry.file(serviceName)
						.findServiceByName(serviceName)
						.findMethodByName(methodName) == null) {
				switch (methodType) {
					case UNARY:
						this.registrar.unary(fullMethodName, requestType, responseType);
						break;
					case SERVER_STREAMING:
						this.registrar.stream(fullMethodName, requestType, responseType);
						break;
					case BIDI_STREAMING:
						this.registrar.bidi(fullMethodName, requestType, responseType);
						break;
					default:
						throw new UnsupportedOperationException();
				}
			}
			else {
				registrar.validate(fullMethodName, requestType, responseType);
			}
			Descriptor inputType = registry.file(serviceName)
				.findServiceByName(serviceName)
				.findMethodByName(methodName)
				.getInputType();
			Descriptor outputType = registry.file(serviceName)
				.findServiceByName(serviceName)
				.findMethodByName(methodName)
				.getOutputType();
			Marshaller<DynamicMessage> responseMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(outputType).build());
			Marshaller<DynamicMessage> requestMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(inputType).build());
			MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
				.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(methodType)
				.setFullMethodName(fullMethodName)
				.setRequestMarshaller(requestMarshaller)
				.setResponseMarshaller(responseMarshaller)
				.setSchemaDescriptor(new SimpleMethodDescriptor(registry.file(serviceName), serviceName, methodName))
				.build();
			ServerCallHandler<DynamicMessage, DynamicMessage> handler = handler(requestType, outputType, function,
					methodType);
			this.handlers.put(methodName, handler);
			this.descriptors.put(methodName, methodDescriptor);
			return this;
		}

		public BindableService build() {
			Builder descriptor = ServiceDescriptor.newBuilder(serviceName);
			descriptor.setSchemaDescriptor(new SimpleBaseDescriptorSupplier(registry.file(serviceName), serviceName));
			for (Map.Entry<String, ServerCallHandler<DynamicMessage, DynamicMessage>> entry : handlers.entrySet()) {
				String methodName = entry.getKey();
				MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = descriptors.get(methodName);
				descriptor.addMethod(methodDescriptor);
			}
			ServerServiceDefinition.Builder service = ServerServiceDefinition.builder(descriptor.build());
			for (Map.Entry<String, ServerCallHandler<DynamicMessage, DynamicMessage>> entry : handlers.entrySet()) {
				String methodName = entry.getKey();
				ServerCallHandler<DynamicMessage, DynamicMessage> handler = entry.getValue();
				MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = descriptors.get(methodName);
				service.addMethod(methodDescriptor, handler);
			}
			return () -> service.build();
		}

		private <I, O> ServerCallHandler<DynamicMessage, DynamicMessage> handler(Class<I> requestType,
				Descriptor descriptor, Function<?, ?> function, MethodType methodType) {
			switch (methodType) {
				case UNARY:
					return ServerCalls.asyncUnaryCall((req, obs) -> {
						I input = converter.convert(req, requestType);
						@SuppressWarnings("unchecked")
						O output = ((Function<I, O>) function).apply(input);
						obs.onNext((DynamicMessage) converter.convert(output, descriptor));
						obs.onCompleted();
					});
				case SERVER_STREAMING:
					return ServerCalls.asyncServerStreamingCall((req, obs) -> {
						I input = converter.convert(req, requestType);
						@SuppressWarnings("unchecked")
						Flux<O> output = Flux.from(((Function<I, Publisher<O>>) function).apply(input));
						output.doOnNext(item -> {
							obs.onNext((DynamicMessage) converter.convert(item, descriptor));
						}).doOnComplete(() -> obs.onCompleted()).doOnError(error -> obs.onError(error)).subscribe();
					});
				case BIDI_STREAMING:
					return ServerCalls.asyncBidiStreamingCall(
							obs -> new BidiStreamObserver<I, O>(function, obs, requestType, descriptor));
				default:
					throw new UnsupportedOperationException("Unsupported method type: " + methodType);
			}
		}

		private class BidiStreamObserver<I, O> implements StreamObserver<DynamicMessage> {

			private final StreamObserver<DynamicMessage> obs;

			private final Class<I> requestType;

			private Many<I> sink;

			private Flux<O> output;

			private Descriptor descriptor;

			@SuppressWarnings("unchecked")
			private BidiStreamObserver(Function<?, ?> function, StreamObserver<DynamicMessage> obs,
					Class<I> requestType, Descriptor descriptor) {
				this.obs = obs;
				this.requestType = requestType;
				this.descriptor = descriptor;
				this.sink = Sinks.many().unicast().onBackpressureBuffer();
				this.output = Flux.from(((Function<Publisher<I>, Publisher<O>>) function).apply(this.sink.asFlux()));
				this.output.doOnNext(item -> {
					obs.onNext((DynamicMessage) converter.convert(item, this.descriptor));
				}).subscribe();
			}

			@Override
			public void onNext(DynamicMessage value) {
				I input = converter.convert(value, requestType);
				this.sink.tryEmitNext(input);
			}

			@Override
			public void onError(Throwable t) {
				obs.onError(t);
				this.sink.tryEmitError(t);
			}

			@Override
			public void onCompleted() {
				obs.onCompleted();
				this.sink.tryEmitComplete();
			}

		}

	}

}
