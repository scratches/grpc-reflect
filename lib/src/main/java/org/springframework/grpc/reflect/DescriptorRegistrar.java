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

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanUtils;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;

import io.grpc.MethodDescriptor.MethodType;

public class DescriptorRegistrar implements DescriptorProvider, FileDescriptorProvider {

	private final DescriptorProtoExtractor protos;
	private final DescriptorCatalog catalog;
	// TODO: get rid of this and all methods that use it
	private final ReflectionFileDescriptorProvider reflection;
	private Map<String, ServiceDescriptorProto> serviceProtos = new HashMap<>();
	private Map<String, List<Class<?>>> typesPerService = new HashMap<>();
	private Map<String, DescriptorMapping> inputs = new HashMap<>();
	private Map<String, DescriptorMapping> outputs = new HashMap<>();
	private Map<Class<?>, Descriptor> descriptors = new HashMap<>();
	private Map<Class<?>, FileDescriptor> types = new HashMap<>();
	private boolean strict = true;

	public DescriptorRegistrar() {
		this(DescriptorProtoExtractor.DEFAULT_INSTANCE, new DescriptorCatalog());
	}

	public DescriptorRegistrar(DescriptorProtoExtractor protos, DescriptorCatalog catalog) {
		this.protos = protos;
		this.catalog = catalog;
		this.reflection = new ReflectionFileDescriptorProvider(protos);
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

	public <I, O> void unary(String fullMethodName, Class<I> input, Class<O> output) {
		MethodDescriptor method = findMethod(catalog, fullMethodName);
		if (method == null) {
			reflection.unary(fullMethodName, input, output);
			method = findMethod(reflection, fullMethodName);
		}
		register(method, input, output);
	}

	public <I, O> void stream(String fullMethodName, Class<I> input, Class<O> output) {
		MethodDescriptor method = findMethod(catalog, fullMethodName);
		if (method == null) {
			reflection.stream(fullMethodName, input, output);
			method = findMethod(reflection, fullMethodName);
		}
		register(method, input, output);
	}

	public <I, O> void bidi(String fullMethodName, Class<I> input, Class<O> output) {
		MethodDescriptor method = findMethod(catalog, fullMethodName);
		if (method == null) {
			reflection.bidi(fullMethodName, input, output);
			method = findMethod(reflection, fullMethodName);
		}
		register(method, input, output);
	}

	private void process(String owner) {
		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		if (this.catalog.file(owner) != null) {
			builder = this.catalog.file(owner).toProto().toBuilder();
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
			this.catalog.register(proto);
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
		this.typesPerService.computeIfAbsent(owner, key -> new java.util.ArrayList<>());
		if (this.typesPerService.get(owner).contains(type)) {
			return false;
		}
		this.typesPerService.get(owner).add(type);
		process(owner, type);
		return true;
	}

	public void register(Class<?> type) {
		String name = DescriptorRegistrar.class.getName();
		if (register(name, type)) {
			process(name);
		}
	}

	public DescriptorMapping input(String fullMethodName) {
		return this.inputs.get(fullMethodName);
	}

	public void input(String fullMethodName, Class<?> type, Descriptor descriptor) {
		this.inputs.put(fullMethodName, new DescriptorMapping(type, descriptor));
	}

	public DescriptorMapping output(String fullMethodName) {
		return this.outputs.get(fullMethodName);
	}

	public void output(String fullMethodName, Class<?> type, Descriptor descriptor) {
		this.outputs.put(fullMethodName, new DescriptorMapping(type, descriptor));
	}

	public void register(Class<?> type, Descriptor descriptor) {
		this.descriptors.put(type, descriptor);
	}

	public void register(FileDescriptor file) {
		for (ServiceDescriptor service : file.getServices()) {
			register(service);
		}
	}

	public void register(ServiceDescriptor service) {
		this.catalog.register(service.getFile());
	}

	public void register(MethodDescriptor method, Class<?> input, Class<?> output) {
		this.catalog.register(method.getFile());
		String name = method.getFullName();
		name = name.substring(0, name.lastIndexOf(method.getName()) - 1) + "/" + method.getName();
		this.input(name, input, method.getInputType());
		this.output(name, output, method.getOutputType());
	}

	private MethodDescriptor findMethod(String fullMethodName) {
		return findMethod(this.catalog, fullMethodName);
	}

	private MethodDescriptor findMethod(FileDescriptorProvider provider, String fullMethodName) {
		String serviceName = fullMethodName.substring(0, fullMethodName.lastIndexOf('/'));
		String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
		FileDescriptor file = provider.file(serviceName);
		if (file == null) {
			return null;
		}
		ServiceDescriptor service = file.findServiceByName(serviceName);
		if (service == null) {
			return null;
		}
		return service.findMethodByName(methodName);
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
			this.catalog.register(proto);
			this.types.put(type, proto);
		} catch (DescriptorValidationException e) {
			throw new IllegalStateException(e);
		}
	}

	// TODO: this class should not implement DescriptorProvider. The Descriptor
	// might be different for the same type and different services.
	@Override
	public Descriptor descriptor(Class<?> clazz) {
		return this.descriptors.get(clazz);
	}

	@Override
	public FileDescriptor file(String serviceName) {
		return this.catalog.file(serviceName);
	}

	public void validate(String fullMethodName, Class<?> requestType, Class<?> responseType) {
		String methodName = fullMethodName.substring(fullMethodName.lastIndexOf('/') + 1);
		String serviceName = fullMethodName.substring(0, fullMethodName.lastIndexOf('/'));
		if (this.catalog.file(serviceName) != null) {
			FileDescriptor file = this.catalog.file(serviceName);
			ServiceDescriptor service = file.findServiceByName(serviceName);
			if (service == null || service.findMethodByName(methodName) == null) {
				throw new IllegalStateException("Service or method not found: " + fullMethodName);
			}
			Descriptor inputType = service.findMethodByName(methodName).getInputType();
			Descriptor outputType = service.findMethodByName(methodName).getOutputType();
			if (this.strict) {
				validateMessage(fullMethodName, requestType, inputType.getFields());
				validateMessage(fullMethodName, responseType, outputType.getFields());
			}
		} else {
			throw new IllegalStateException("Service not registered: " + fullMethodName);
		}
	}

	private void validateMessage(String fullMethodName, Class<?> responseType, List<FieldDescriptor> fields) {
		for (FieldDescriptor field : fields) {
			PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(responseType, field.getName());
			if (descriptor == null) {
				throw new IllegalArgumentException("Field " + field.getName() + " not found in class "
						+ responseType.getName() + " for method " + fullMethodName);
			}
			// TODO: map types
			if (field.isRepeated() && !descriptor.getPropertyType().isArray()
					&& !Iterable.class.isAssignableFrom(descriptor.getPropertyType())) {
				throw new IllegalArgumentException(
						"Field " + field.getName() + " is repeated in the schema, but is not a collection in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
				validateMessage(fullMethodName, descriptor.getPropertyType(), field.getMessageType().getFields());
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.ENUM) {
				if (!descriptor.getPropertyType().isEnum()) {
					throw new IllegalArgumentException(
							"Field " + field.getName() + " is an enum in the schema, but is not in class "
									+ responseType.getName() + " for method " + fullMethodName);
				}
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.BOOLEAN
					&& !descriptor.getPropertyType().equals(Boolean.class)
					&& !descriptor.getPropertyType().equals(boolean.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is a boolean in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if ((field.getJavaType() == FieldDescriptor.JavaType.STRING
					|| field.getJavaType() == FieldDescriptor.JavaType.BYTE_STRING)
					&& !descriptor.getPropertyType().equals(String.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is a string in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.DOUBLE
					&& !descriptor.getPropertyType().equals(Double.class)
					&& !descriptor.getPropertyType().equals(double.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is a double in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.FLOAT
					&& !descriptor.getPropertyType().equals(Float.class)
					&& !descriptor.getPropertyType().equals(float.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is a float in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.INT
					&& !descriptor.getPropertyType().equals(Integer.class)
					&& !descriptor.getPropertyType().equals(int.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is an integer in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
			if (field.getJavaType() == FieldDescriptor.JavaType.LONG
					&& !descriptor.getPropertyType().equals(Long.class)
					&& !descriptor.getPropertyType().equals(long.class)) {
				throw new IllegalArgumentException(
						"Field '" + field.getName() + "' is a long in the schema, but is not in class "
								+ responseType.getName() + " for method " + fullMethodName);
			}
		}
		// All fields in the input type must be present in the class
	}
}
