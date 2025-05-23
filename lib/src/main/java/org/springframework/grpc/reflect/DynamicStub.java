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

import com.google.protobuf.DynamicMessage;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;

public class DynamicStub extends AbstractStub<DynamicStub> {

	private final MessageConverter converter;
	private final DescriptorRegistry registry;

	public DynamicStub(DescriptorRegistry registry, Channel channel) {
		this(registry, channel, CallOptions.DEFAULT);
	}

	public DynamicStub(DescriptorRegistry registry, Channel channel, CallOptions callOptions) {
		super(channel, callOptions);
		this.registry = registry;
		this.converter = new MessageConverter(registry);
	}

	public static DynamicStub newStub(Channel channel) {
		return new DynamicStub(new DescriptorRegistry(), channel);
	}

	public <T> T call(String fullMethodName, Object request, Class<T> responseType) {
		if (request == null) {
			throw new IllegalArgumentException("Request cannot be null");
		}
		if (responseType == null) {
			throw new IllegalArgumentException("Response type cannot be null");
		}
		if (this.registry.descriptor(responseType) == null) {
			this.registry.register(responseType);
		}
		if (this.registry.descriptor(request.getClass()) == null) {
			this.registry.register(request.getClass());
		}
		Marshaller<DynamicMessage> marshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(registry.descriptor(responseType)).build());
		MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
				.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(MethodDescriptor.MethodType.UNARY)
				.setFullMethodName(fullMethodName)
				.setRequestMarshaller(marshaller)
				.setResponseMarshaller(marshaller)
				.build();
		var response = ClientCalls.blockingUnaryCall(getChannel(), methodDescriptor, getCallOptions(),
				(DynamicMessage) converter.convert(request));
		return converter.convert(response, responseType);
	}

	@Override
	protected DynamicStub build(Channel channel, CallOptions callOptions) {
		return new DynamicStub(this.registry, channel, callOptions);
	}

}
