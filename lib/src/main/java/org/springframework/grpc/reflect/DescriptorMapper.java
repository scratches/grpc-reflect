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
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Provider interface for obtaining protocol buffer descriptors.
 * <p>
 * This interface defines the contract for classes that can supply descriptor information
 * used in gRPC reflection and service discovery mechanisms.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public interface DescriptorMapper {

	Descriptor descriptor(Class<?> clazz);

	static DescriptorMapper DEFAULT_INSTANCE = new DescriptorMapper() {

		private Map<Class<?>, Descriptor> cache = new HashMap<>();

		private Map<Class<?>, DescriptorProto> protos = new HashMap<>();

		@Override
		public Descriptor descriptor(Class<?> clazz) {
			if (clazz == null) {
				clazz = Void.class;
			}
			if (this.cache.containsKey(clazz)) {
				return this.cache.get(clazz);
			}
			Set<Class<?>> current = new HashSet<>();
			FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
			return descriptor(clazz, current, builder);
		}

		private Descriptor descriptor(Class<?> clazz, Set<Class<?>> current, FileDescriptorProto.Builder builder) {
			current.add(clazz);
			if (this.cache.containsKey(clazz)) {
				return this.cache.get(clazz);
			}
			DescriptorProto message = proto(clazz);
			builder.addMessageType(message);
			try {
				Set<FileDescriptor> dependencies = new HashSet<>();
				for (var field : message.getFieldList()) {
					if (field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
						PropertyDescriptor property = BeanUtils.getPropertyDescriptor(clazz, field.getName());
						Class<?> fieldType = property.getReadMethod().getReturnType();
						if (Map.class.isAssignableFrom(fieldType)) {
							Class<?> valueType = findGenericType(property.getReadMethod().getGenericReturnType(), 1);
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							}
							else {
								continue;
							}
						}
						else if (Iterable.class.isAssignableFrom(fieldType)) {
							Class<?> valueType = findGenericType(property.getReadMethod().getGenericReturnType(), 0);
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							}
							else {
								continue;
							}
						}
						else if (fieldType.isArray()) {
							Class<?> valueType = fieldType.arrayType();
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							}
							else {
								continue;
							}
						}
						if (fieldType != null && fieldType != clazz) {
							if (current.contains(fieldType)) {
								DescriptorProto proto = proto(fieldType);
								if (!builder.getMessageTypeList().contains(proto)) {
									builder.addMessageType(proto);
								}
							}
							else {
								Descriptor descriptor = descriptor(fieldType, current, builder);
								this.cache.put(fieldType, descriptor);
								dependencies.add(descriptor.getFile());
							}
						}
					}
				}
				if (this.cache.containsKey(clazz)) {
					current.remove(clazz);
					return this.cache.get(clazz);
				}
				FileDescriptor proto = FileDescriptor.buildFrom(builder.build(),
						dependencies.toArray(new FileDescriptor[0]));
				for (Class<?> other : new HashSet<>(current)) {
					Descriptor maybe = proto.findMessageTypeByName(other.getSimpleName());
					if (maybe != null) {
						this.cache.put(other, maybe);
					}
				}
				return proto.findMessageTypeByName(message.getName());
			}
			catch (DescriptorValidationException e) {
				throw new IllegalStateException(e);
			}

		}

		private DescriptorProto proto(Class<?> clazz) {
			if (this.protos.containsKey(clazz)) {
				return this.protos.get(clazz);
			}
			DescriptorProto.Builder builder = DescriptorProto.newBuilder();
			builder.setName(clazz.getSimpleName());
			if (clazz == Void.class || clazz == Void.TYPE) {
				return builder.build();
			}
			Map<Integer, PropertyDescriptor> properties = orderedProperties(clazz);
			for (Integer count : properties.keySet()) {
				PropertyDescriptor property = properties.get(count);
				Class<?> fieldType = property.getReadMethod().getReturnType();
				Type type = findType(fieldType, property.getReadMethod().getGenericReturnType());
				DescriptorProtos.FieldDescriptorProto.Builder fb = DescriptorProtos.FieldDescriptorProto.newBuilder()
					.setName(property.getName())
					.setNumber(count)
					.setType(type);
				if (type == Type.TYPE_MESSAGE) {
					if (Map.class.isAssignableFrom(fieldType)) {
						String mapTypeName = StringUtils.capitalize(property.getName()) + "Entry";
						fb.setTypeName(mapTypeName);
						builder.addNestedType(mapType(mapTypeName, property));
					}
					else if (Iterable.class.isAssignableFrom(fieldType) || fieldType.isArray()) {
						fb.setTypeName(findGenericTypeName(property.getReadMethod().getGenericReturnType(), 0));
					}
					else {
						fb.setTypeName(fieldType.getSimpleName());
					}
				}
				if (fieldType.isArray() || Iterable.class.isAssignableFrom(fieldType)
						|| Map.class.isAssignableFrom(fieldType)) {
					fb.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
				}
				builder.addField(fb.build());
				count++;
			}
			DescriptorProto result = builder.build();
			this.protos.put(clazz, result);
			return result;
		};

		private Map<Integer, PropertyDescriptor> orderedProperties(Class<?> clazz) {
			Map<Integer, PropertyDescriptor> orders = new TreeMap<>();
			Map<Integer, PropertyDescriptor> declarations = declarationOrder(clazz);
			for (Integer order : declarations.keySet()) {
				PropertyDescriptor property = declarations.get(order);
				Field field = ReflectionUtils.findField(clazz, property.getName());
				if (field == null) {
					continue;
				}
				ReflectionUtils.makeAccessible(field);
				Integer maybe = OrderUtils.getOrder(field);
				if (maybe != null) {
					orders.put(maybe, property);
				}
				else {
					orders.put(order, property);
				}
			}
			// Not an error if the explicit orders leave gaps
			if (orders.size() != declarations.size()) {
				// Some properties were not assigned an order
				throw new IllegalStateException("Some properties were assigned a duplicate order in " + clazz);
			}
			return orders;
		}

		private Map<Integer, PropertyDescriptor> declarationOrder(Class<?> clazz) {
			PropertyDescriptor[] properties = BeanUtils.getPropertyDescriptors(clazz);
			Map<String, PropertyDescriptor> map = new HashMap<>();
			for (PropertyDescriptor property : properties) {
				Class<?> fieldType = property.getReadMethod() != null ? property.getReadMethod().getReturnType() : null;
				if (fieldType == null || fieldType == Class.class) {
					continue;
				}
				map.put(property.getName(), property);
			}

			Map<Integer, PropertyDescriptor> orders = new HashMap<>();
			if (clazz.isRecord()) {
				int i = 1;
				for (RecordComponent component : clazz.getRecordComponents()) {
					if (map.containsKey(component.getName())) {
						orders.put(i++, map.get(component.getName()));
					}
				}
				return orders;
			}
			Map<String, Class<?>> owners = new HashMap<>();
			for (PropertyDescriptor property : map.values()) {
				Field field = ReflectionUtils.findField(clazz, property.getName());
				owners.put(property.getName(), field.getDeclaringClass());
			}
			Class<?> current = clazz;
			List<Class<?>> classes = new ArrayList<>();
			while (current != null && current != Object.class) {
				classes.add(current);
				current = current.getSuperclass();
			}
			// Sort fields by declaration order with superclasses first
			int count = 1;
			Collections.reverse(classes);
			for (Class<?> owner : classes) {
				// Order of declaration is not defined in Java reflection, but it
				// usually works the way you expect anyway, at least for openjdk.
				for (Field field : owner.getDeclaredFields()) {
					if (owners.containsKey(field.getName())) {
						orders.put(count, map.get(field.getName()));
						count++;
					}
				}
			}
			return orders;
		}

		private static DescriptorProtos.FieldDescriptorProto.Type findType(Class<?> type,
				java.lang.reflect.Type genericType) {
			if (type == null) {
				return null; // or MESSAGE?
			}
			if (type.isArray()) {
				type = type.getComponentType();
			}
			if (Iterable.class.isAssignableFrom(type)) {
				if (genericType instanceof ParameterizedType param) {
					param.getActualTypeArguments();
					if (param.getActualTypeArguments().length > 0) {
						type = (Class<?>) param.getActualTypeArguments()[0];
					}
				}
			}
			if (Map.class.isAssignableFrom(type)) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
			}
			if (type == String.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
			}
			else if (type == float.class || type == Float.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT;
			}
			else if (type == double.class || type == Double.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE;
			}
			else if (type == int.class || type == Integer.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32;
			}
			else if (type == long.class || type == Long.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64;
			}
			else if (type == boolean.class || type == Boolean.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL;
			}
			else if (type == byte[].class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES;
			}
			else if (type.isEnum()) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
			}
			return DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
		}

		static DescriptorProto mapType(String name, PropertyDescriptor field) {
			DescriptorProto.Builder type = DescriptorProto.newBuilder().setName(name);
			FieldDescriptorProto.Builder key = FieldDescriptorProto.newBuilder()
				.setName("key")
				.setNumber(1)
				.setType(findKeyType(field.getReadMethod().getGenericReturnType()));
			FieldDescriptorProto.Builder value = FieldDescriptorProto.newBuilder()
				.setName("value")
				.setNumber(2)
				.setType(findValueType(field.getReadMethod().getGenericReturnType()));
			if (value.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
					|| value.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
				value.setTypeName(findGenericTypeName(field.getReadMethod().getGenericReturnType(), 1));
			}
			type.setOptions(type.getOptionsBuilder().setMapEntry(true));
			type.addField(key.build());
			type.addField(value.build());
			return type.build();
		}

		static Class<?> findGenericType(java.lang.reflect.Type genericType, int index) {
			if (genericType instanceof ParameterizedType param) {
				if (param.getActualTypeArguments().length > index) {
					Class<?> keyType = (Class<?>) param.getActualTypeArguments()[index];
					return keyType;
				}
			}
			throw new UnsupportedOperationException("Unsupported generic value type: " + genericType);
		}

		static String findGenericTypeName(java.lang.reflect.Type genericType, int index) {
			return findGenericType(genericType, index).getSimpleName();
		}

		static Type findKeyType(java.lang.reflect.Type genericType) {
			if (genericType instanceof ParameterizedType param) {
				if (param.getActualTypeArguments().length > 0) {
					Class<?> keyType = (Class<?>) param.getActualTypeArguments()[0];
					Type type = findType(keyType, null);
					if (type != Type.TYPE_MESSAGE && type != Type.TYPE_ENUM) {
						// Keys in protobuf maps cannot be messages or enums
						return type;
					}
				}
			}
			throw new UnsupportedOperationException("Unsupported map key type: " + genericType);
		}

		static Type findValueType(java.lang.reflect.Type genericType) {
			if (genericType instanceof ParameterizedType param) {
				if (param.getActualTypeArguments().length > 1) {
					Class<?> keyType = (Class<?>) param.getActualTypeArguments()[1];
					Type type = findType(keyType, null);
					return type;
				}
			}
			throw new UnsupportedOperationException("Unsupported map value type: " + genericType);
		}

	};

}
