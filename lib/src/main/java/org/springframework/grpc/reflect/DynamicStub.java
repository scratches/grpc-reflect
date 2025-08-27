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

import org.reactivestreams.Publisher;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Descriptors.Descriptor;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

public class DynamicStub extends AbstractStub<DynamicStub> {

	private final MessageConverter converter;
	private final DescriptorRegistrar registry;

	public DynamicStub(DescriptorRegistrar registry, Channel channel) {
		this(registry, channel, CallOptions.DEFAULT);
	}

	public DynamicStub(DescriptorRegistrar registry, Channel channel, CallOptions callOptions) {
		super(channel, callOptions);
		this.registry = registry;
		this.converter = new MessageConverter();
	}

	public static DynamicStub newStub(Channel channel) {
		return new DynamicStub(new DescriptorRegistrar(), channel);
	}

	public <S, T> Flux<T> bidi(String fullMethodName, Publisher<S> request, Class<S> requestType,
			Class<T> responseType) {
		if (request == null) {
			throw new IllegalArgumentException("Request cannot be null");
		}
		if (responseType == null) {
			throw new IllegalArgumentException("Response type cannot be null");
		}
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = methodDescriptor(fullMethodName,
				requestType, responseType, MethodDescriptor.MethodType.BIDI_STREAMING);
		Many<T> sink = Sinks.many().multicast().onBackpressureBuffer();
		StreamObserver<DynamicMessage> requests = ClientCalls.asyncBidiStreamingCall(
				getChannel().newCall(methodDescriptor, getCallOptions()),
				new StreamObserver<DynamicMessage>() {

					@Override
					public void onNext(DynamicMessage value) {
						sink.tryEmitNext(converter.convert(value, responseType));
					}

					@Override
					public void onError(Throwable t) {
						sink.tryEmitError(t);
					}

					@Override
					public void onCompleted() {
						sink.tryEmitComplete();
					}

				});
		Flux.from(request).doOnComplete(() -> requests.onCompleted()).doOnError(error -> requests.onError(error))
				.doOnNext(msg -> requests
						.onNext((DynamicMessage) converter.convert(msg, this.registry.descriptor(responseType))))
				.subscribe();
		return sink.asFlux();
	}

	public <T> Flux<T> stream(String fullMethodName, Object request, Class<T> responseType) {
		if (request == null) {
			throw new IllegalArgumentException("Request cannot be null");
		}
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = methodDescriptor(fullMethodName,
				request.getClass(), responseType, MethodDescriptor.MethodType.SERVER_STREAMING);
		Many<T> sink = Sinks.many().multicast().onBackpressureBuffer();
		ClientCalls.asyncServerStreamingCall(getChannel().newCall(methodDescriptor, getCallOptions()),
				(DynamicMessage) converter.convert(request, this.registry.descriptor(request.getClass())),
				new StreamObserver<DynamicMessage>() {

					@Override
					public void onNext(DynamicMessage value) {
						sink.tryEmitNext(converter.convert(value, responseType));
					}

					@Override
					public void onError(Throwable t) {
						sink.tryEmitError(t);
					}

					@Override
					public void onCompleted() {
						sink.tryEmitComplete();
					}

				});
		return sink.asFlux();
	}

	public <T> T unary(String fullMethodName, Object request, Class<T> responseType) {
		if (request == null) {
			throw new IllegalArgumentException("Request cannot be null");
		}
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = methodDescriptor(fullMethodName,
				request.getClass(),
				responseType, MethodDescriptor.MethodType.UNARY);
		var response = ClientCalls.blockingUnaryCall(getChannel(), methodDescriptor, getCallOptions(),
				(DynamicMessage) converter.convert(request, registry.descriptor(request.getClass())));
		return converter.convert(response, responseType);
	}

	private <T> MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor(String fullMethodName,
			Class<?> requestType, Class<T> responseType, MethodDescriptor.MethodType methodType) {
		if (responseType == null) {
			throw new IllegalArgumentException("Response type cannot be null");
		}
		if (this.registry.descriptor(responseType) == null) {
			this.registry.register(responseType);
		}
		if (this.registry.descriptor(requestType) == null) {
			this.registry.register(requestType);
		}
		Marshaller<DynamicMessage> requestMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(registry.descriptor(requestType)).build());
		Marshaller<DynamicMessage> responseMarshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(registry.descriptor(responseType)).build());
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
				.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(methodType)
				.setFullMethodName(fullMethodName)
				.setRequestMarshaller(requestMarshaller)
				.setResponseMarshaller(responseMarshaller)
				.build();
		return methodDescriptor;
	}

	@Override
	protected DynamicStub build(Channel channel, CallOptions callOptions) {
		return new DynamicStub(this.registry, channel, callOptions);
	}

}
