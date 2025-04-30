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
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;

public class DescriptorRegistry implements DescriptorProvider {

	private final DescriptorProtoProvider protos;
	private final MethodDescriptorProtoProvider methods;
	private Map<String, ServiceDescriptorProto> serviceProtos = new HashMap<>();
	private Map<String, List<Class<?>>> types = new HashMap<>();
	private Map<Class<?>, Descriptor> descriptors = new HashMap<>();
	private Map<String, MethodDescriptor> methodDescriptors = new HashMap<>();
	private Map<String, FileDescriptor> fileDescriptors = new HashMap<>();

	public DescriptorRegistry() {
		this(DescriptorProtoProvider.DEFAULT_INSTANCE, MethodDescriptorProtoProvider.DEFAULT_INSTANCE);
	}

	public DescriptorRegistry(DescriptorProtoProvider protos, MethodDescriptorProtoProvider methods) {
		this.protos = protos;
		this.methods = methods;
	}

	public <I, O> void register(String fullMethodName, Class<I> input, Class<O> output) {
		String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
		String serviceName = fullMethodName.substring(0, fullMethodName.lastIndexOf('/'));
		MethodDescriptorProto proto = MethodDescriptorProto.newBuilder()
				.setName(methodName)
				.setInputType(input.getSimpleName())
				.setOutputType(output.getSimpleName())
				.build();
		register(DescriptorRegistry.class, serviceName, methodName, proto, input, output);
	}

	public void register(Method method) {
		Class<?> owner = method.getDeclaringClass();
		MethodDescriptorProto proto = this.methods.service(method);
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();
		register(owner, owner.getSimpleName(), proto.getName(), proto, inputType, outputType);
	}

	private void register(Class<?> owner, String serviceName, String methodName, MethodDescriptorProto proto,
			Class<?> input,
			Class<?> output) {
		ServiceDescriptorProto service = this.serviceProtos.get(serviceName);
		if (service == null) {
			service = ServiceDescriptorProto.newBuilder().setName(serviceName).build();
		}
		if (findMethod(service, methodName) != null) {
			return;
		}
		ServiceDescriptorProto.Builder builder = service.toBuilder();
		builder.addMethod(proto);
		service = builder.build();
		this.serviceProtos.put(serviceName, service);
		register(serviceName, input);
		register(serviceName, output);
		process(serviceName);
	}

	private MethodDescriptorProto findMethod(ServiceDescriptorProto service, String name) {
		for (MethodDescriptorProto method : service.getMethodList()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		return null;
	}

	private void process(String owner) {
		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		if (this.fileDescriptors.containsKey(owner)) {
			builder = this.fileDescriptors.get(owner).toProto().toBuilder();
		} else {
			builder.setName(owner + ".proto");
			builder.setSyntax("proto3");
		}
		ServiceDescriptorProto service = this.serviceProtos.get(owner);
		if (service != null) {
			removeService(builder, service.getName());
			builder.addService(service);
		}
		Map<String, Class<?>> types = new HashMap<>();
		for (Class<?> type : this.types.get(owner)) {
			DescriptorProto message = this.protos.proto(type);
			if (findMessage(builder, message.getName()) != null) {
				continue;
			}
			builder.addMessageType(message);
			types.put(message.getName(), type);
		}
		try {
			FileDescriptor proto = FileDescriptor.buildFrom(builder.build(), new FileDescriptor[0]);
			proto.getMessageTypes()
					.forEach(descriptor -> this.descriptors.put(types.get(descriptor.getName()), descriptor));
			proto.getServices().forEach(descriptor -> {
				for (MethodDescriptor method : descriptor.getMethods()) {
					this.methodDescriptors.put(descriptor.getName() + "/" + method.getName(), method);
				}
				this.fileDescriptors.put(descriptor.getName(), proto);
			});
		} catch (DescriptorValidationException e) {
			throw new IllegalStateException(e);
		}
	}

	private DescriptorProto findMessage(Builder builder, String name) {
		for (int i = 0; i < builder.getMessageTypeCount(); i++) {
			if (builder.getMessageType(i).getName().equals(name)) {
				return builder.getMessageType(i);
			}
		}
		return null;
	}

	private void removeService(Builder builder, String name) {
		for (int i = 0; i < builder.getServiceCount(); i++) {
			if (builder.getService(i).getName().equals(name)) {
				builder.removeService(i);
				break;
			}
		}
	}

	private boolean register(String owner, Class<?> type) {
		this.types.computeIfAbsent(owner, key -> new java.util.ArrayList<>());
		if (this.types.get(owner).contains(type)) {
			return false;
		}
		this.types.get(owner).add(type);
		return true;
	}

	public void register(Class<?> type) {
		String name = DescriptorRegistry.class.getName();
		if (register(name, type)) {
			process(name);
		}
	}

	@Override
	public Descriptor descriptor(Class<?> clazz) {
		return this.descriptors.get(clazz);
	}

	@Override
	public MethodDescriptor method(String fullMethodName) {
		return this.methodDescriptors.get(fullMethodName);
	}

	@Override
	public FileDescriptor file(String serviceName) {
		return this.fileDescriptors.get(serviceName);
	}
}
