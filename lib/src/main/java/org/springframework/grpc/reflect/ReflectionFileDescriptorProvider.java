/*
 * Copyright 2025-current the original author or authors.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.grpc.MethodDescriptor.MethodType;

public class ReflectionFileDescriptorProvider implements FileDescriptorProvider {

	private final DescriptorProtoExtractor protos;
	private final Map<String, FileDescriptor> catalog = new HashMap<>();
	// TODO: wrong
	private Map<Class<?>, FileDescriptor> types = new HashMap<>();
	// TODO: wrong
	private Map<Class<?>, Descriptor> descriptors = new HashMap<>();
	private Map<String, ServiceDescriptorProto> serviceProtos = new HashMap<>();
	private Map<String, List<Class<?>>> typesPerService = new HashMap<>();

	public ReflectionFileDescriptorProvider() {
		this(DescriptorProtoExtractor.DEFAULT_INSTANCE);
	}

	public ReflectionFileDescriptorProvider(DescriptorProtoExtractor protos) {
		this.protos = protos;
	}

	@Override
	public FileDescriptor file(String serviceName) {
		return this.catalog.get(serviceName);
	}

	public <I, O> void unary(String fullMethodName, Class<I> input, Class<O> output) {
		register(fullMethodName, input, output, MethodType.UNARY);
	}

	public <I, O> void stream(String fullMethodName, Class<I> input, Class<O> output) {
		register(fullMethodName, input, output, MethodType.SERVER_STREAMING);
	}

	public <I, O> void bidi(String fullMethodName, Class<I> input, Class<O> output) {
		register(fullMethodName, input, output, MethodType.BIDI_STREAMING);
	}

	public void register(Method method) {
		Class<?> owner = method.getDeclaringClass();
		Class<?> inputType = method.getParameterTypes()[0];
		Class<?> outputType = method.getReturnType();

		unary(owner.getSimpleName() + "/" + StringUtils.capitalize(method.getName()), inputType,
				outputType);
	}

	private <I, O> void register(String fullMethodName, Class<I> input, Class<O> output, MethodType methodType) {
		String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
		String serviceName = fullMethodName.substring(0, fullMethodName.lastIndexOf('/'));
		MethodDescriptorProto.Builder builder = MethodDescriptorProto.newBuilder()
				.setName(methodName)
				.setInputType(input.getSimpleName())
				.setOutputType(output.getSimpleName());
		switch (methodType) {
			case SERVER_STREAMING:
				builder.setServerStreaming(true);
				break;
			case BIDI_STREAMING:
				builder.setServerStreaming(true);
				builder.setClientStreaming(true);
				break;
			default:
		}
		MethodDescriptorProto proto = builder
				.build();
		register(DescriptorRegistrar.class, serviceName, methodName, proto, input, output);
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

	private boolean register(String owner, Class<?> type) {
		this.typesPerService.computeIfAbsent(owner, key -> new java.util.ArrayList<>());
		if (this.typesPerService.get(owner).contains(type)) {
			return false;
		}
		this.typesPerService.get(owner).add(type);
		process(owner, type);
		return true;
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
		if (this.catalog.get(owner) != null) {
			builder = this.catalog.get(owner).toProto().toBuilder();
		} else {
			builder.setName(owner + ".proto");
			builder.setSyntax("proto3");
		}
		ServiceDescriptorProto service = this.serviceProtos.get(owner);
		if (service != null) {
			removeService(builder, service.getName());
			builder.addService(service);
		}
		Set<FileDescriptor> dependencies = new HashSet<>();
		for (Class<?> type : this.typesPerService.get(owner)) {
			FileDescriptor message = this.types.get(type);
			dependencies.add(message);
			if (builder.getDependencyList().contains(message.getName())) {
				continue;
			}
			builder.addDependency(message.getName());
		}
		try {
			FileDescriptor proto = FileDescriptor.buildFrom(builder.build(),
					dependencies.toArray(new FileDescriptor[0]));
			this.catalog.put(service.getName(), proto);
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

	private void process(String owner, Class<?> type) {
		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		if (this.types.containsKey(type)) {
			builder = this.types.get(type).toProto().toBuilder();
		} else {
			builder.setName(type.getSimpleName() + ".proto");
			builder.setSyntax("proto3");
		}
		Map<String, Class<?>> types = new HashMap<>();
		DescriptorProto message = this.protos.proto(type);
		if (findMessage(builder, message.getName()) == null) {
			builder.addMessageType(message);
			types.put(message.getName(), type);
		}
		Set<FileDescriptor> dependencies = new HashSet<>();
		for (Class<?> dep : this.typesPerService.get(owner)) {
			FileDescriptor file = this.types.get(dep);
			if (file == null) {
				continue;
			}
			dependencies.add(file);
		}
		try {
			FileDescriptor proto = FileDescriptor.buildFrom(builder.build(),
					dependencies.toArray(new FileDescriptor[0]));
			proto.getMessageTypes()
					.forEach(descriptor -> this.descriptors.put(types.get(descriptor.getName()), descriptor));
			proto.getServices()
					.forEach(descriptor -> this.catalog.put(descriptor.getName(), proto));
			this.types.put(type, proto);
		} catch (DescriptorValidationException e) {
			throw new IllegalStateException(e);
		}
	}
}
