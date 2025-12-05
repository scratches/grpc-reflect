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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
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
 * This interface defines the contract for classes that can supply descriptor
 * information used in gRPC reflection and service discovery mechanisms.
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
			return descriptor(clazz, current);
		}

		private Descriptor descriptor(Class<?> clazz, Set<Class<?>> current) {
			current.add(clazz);
			DescriptorProto message = proto(clazz);
			FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
			builder.addMessageType(message);
			try {
				Set<FileDescriptor> dependencies = new HashSet<>();
				for (var field : message.getFieldList()) {
					if (field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
						@Nullable
						Field property = ReflectionUtils.findField(clazz, field.getName());
						Class<?> fieldType = property.getType();
						if (Map.class.isAssignableFrom(fieldType)) {
							Class<?> valueType = findGenericType(property.getGenericType(), 1);
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							} else {
								continue;
							}
						} else if (Iterable.class.isAssignableFrom(fieldType)) {
							Class<?> valueType = findGenericType(property.getGenericType(), 0);
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							} else {
								continue;
							}
						} else if (fieldType.isArray()) {
							Class<?> valueType = fieldType.arrayType();
							Type type = findType(valueType, null);
							if (type == Type.TYPE_MESSAGE || type == Type.TYPE_ENUM) {
								fieldType = valueType;
							} else {
								continue;
							}
						}
						if (fieldType != null && fieldType != clazz) {
							if (current.contains(fieldType)) {
								builder.addMessageType(proto(fieldType));
							} else {
								Descriptor descriptor = descriptor(fieldType, current);
								this.cache.put(fieldType, descriptor);
								dependencies.add(descriptor.getFile());
							}
						}
					}
				}
				if (this.cache.containsKey(clazz)) {
					return this.cache.get(clazz);
				}
				FileDescriptor proto = FileDescriptor.buildFrom(builder.build(),
						dependencies.toArray(new FileDescriptor[0]));
				Descriptor result = proto.findMessageTypeByName(message.getName());
				this.cache.put(clazz, result);
				return result;
			} catch (DescriptorValidationException e) {
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
			int count = 1;
			for (var field : clazz.getDeclaredFields()) {
				Type type = findType(field.getType(), field.getGenericType());
				DescriptorProtos.FieldDescriptorProto.Builder fb = DescriptorProtos.FieldDescriptorProto.newBuilder()
						.setName(field.getName())
						.setNumber(count)
						.setType(type);
				if (type == Type.TYPE_MESSAGE) {
					if (Map.class.isAssignableFrom(field.getType())) {
						String mapTypeName = StringUtils.capitalize(field.getName()) + "Entry";
						fb.setTypeName(mapTypeName);
						builder.addNestedType(mapType(mapTypeName, field));
					} else if (Iterable.class.isAssignableFrom(field.getType()) || field.getType().isArray()) {
						fb.setTypeName(findGenericTypeName(field.getGenericType(), 0));
					} else {
						fb.setTypeName(field.getType().getSimpleName());
					}
				}
				if (field.getType().isArray() || Iterable.class.isAssignableFrom(field.getType())
						|| Map.class.isAssignableFrom(field.getType())) {
					fb.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
				}
				builder.addField(fb.build());
				count++;
			}
			DescriptorProto result = builder.build();
			this.protos.put(clazz, result);
			return result;
		};

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
			} else if (type == float.class || type == Float.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT;
			} else if (type == double.class || type == Double.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE;
			} else if (type == int.class || type == Integer.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32;
			} else if (type == long.class || type == Long.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64;
			} else if (type == boolean.class || type == Boolean.class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL;
			} else if (type == byte[].class) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES;
			} else if (type.isEnum()) {
				return DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
			}
			return DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
		}

		static DescriptorProto mapType(String name, Field field) {
			DescriptorProto.Builder type = DescriptorProto.newBuilder().setName(name);
			FieldDescriptorProto.Builder key = FieldDescriptorProto.newBuilder()
					.setName("key")
					.setNumber(1)
					.setType(findKeyType(field.getGenericType()));
			FieldDescriptorProto.Builder value = FieldDescriptorProto.newBuilder()
					.setName("value")
					.setNumber(2)
					.setType(findValueType(field.getGenericType()));
			if (value.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
					|| value.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
				value.setTypeName(findGenericTypeName(field.getGenericType(), 1));
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
