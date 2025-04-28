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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

public class DescriptorRegistry implements DescriptorProvider {

	private final DescriptorProtoProvider protos;
	private final MethodDescriptorProtoProvider methods;
	private Map<Class<?>, ServiceDescriptorProto> serviceProtos = new HashMap<>();
	private Map<Class<?>, List<Class<?>>> types = new HashMap<>();
	private Map<Class<?>, Descriptor> descriptors = new HashMap<>();

	public DescriptorRegistry() {
		this(DescriptorProtoProvider.DEFAULT_INSTANCE, MethodDescriptorProtoProvider.DEFAULT_INSTANCE);
	}

	public DescriptorRegistry(DescriptorProtoProvider protos, MethodDescriptorProtoProvider methods) {
		this.protos = protos;
		this.methods = methods;
	}

	public void register(Method method) {
		Class<?> owner = method.getDeclaringClass();
		ServiceDescriptorProto service = this.serviceProtos.get(owner);
		if (service == null) {
			service = ServiceDescriptorProto.newBuilder().setName(owner.getSimpleName()).build();
		}
		if (findMethod(service, method.getName()) != null) {
			return;
		}
		ServiceDescriptorProto.Builder builder = service.toBuilder();
		builder.addMethod(this.methods.service(method));
		service = builder.build();
		this.serviceProtos.put(owner, service);
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();
		if (inputType == void.class) {
			inputType = Void.class;
		}
		if (outputType == void.class) {
			outputType = Void.class;
		}
		this.types.computeIfAbsent(owner, k -> new java.util.ArrayList<>()).add(inputType);
		this.types.computeIfAbsent(owner, k -> new java.util.ArrayList<>()).add(outputType);
		process(owner);
	}

	private MethodDescriptorProto findMethod(ServiceDescriptorProto service, String name) {
		for (MethodDescriptorProto method : service.getMethodList()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		return null;
	}

	private void process(Class<?> owner) {
		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		builder.setName(owner.getName() + ".proto");
		builder.setSyntax("proto3");
		ServiceDescriptorProto service = this.serviceProtos.get(owner);
		if (service != null) {
			builder.addService(service);
		}
		Map<String, Class<?>> types = new HashMap<>();
		for (Class<?> type : this.types.get(owner)) {
			DescriptorProto message = this.protos.proto(type);
			if (types.containsKey(message.getName())) {
				continue;
			}
			builder.addMessageType(message);
			types.put(message.getName(), type);
		}
		try {
			FileDescriptor proto = FileDescriptor.buildFrom(builder.build(), new FileDescriptor[0]);
			proto.getMessageTypes()
					.forEach(descriptor -> this.descriptors.put(types.get(descriptor.getName()), descriptor));
		} catch (DescriptorValidationException e) {
			throw new IllegalStateException(e);
		}
	}

	public void register(Class<?> type) {
		this.types.computeIfAbsent(DescriptorRegistry.class, key -> new java.util.ArrayList<>());
		if (this.types.get(DescriptorRegistry.class).contains(type)) {
			return;
		}
		this.types.get(DescriptorRegistry.class).add(type);
		process(DescriptorRegistry.class);
	}

	@Override
	public Descriptor descriptor(Class<?> clazz) {
		return this.descriptors.get(clazz);
	}
}
