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
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DynamicMessage.Builder;

/**
 * Converter for transforming between protocol buffer messages and other formats.
 * <p>
 * This class provides conversion capabilities for gRPC message types, enabling seamless
 * integration between protocol buffer messages and other data representations used in
 * Spring applications.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class MessageConverter {

	public <T> T convert(AbstractMessage message, Class<T> targetType) {
		if (message == null || targetType == null || targetType == Void.class) {
			return null;
		}
		if (targetType.isInstance(message)) {
			return targetType.cast(message);
		}
		T instance = BeanUtils.instantiateClass(targetType);
		for (PropertyDescriptor propertyDescriptor : BeanUtils.getPropertyDescriptors(targetType)) {
			String propertyName = propertyDescriptor.getName();
			if (Collection.class.isAssignableFrom(propertyDescriptor.getPropertyType())) {
				@SuppressWarnings("unchecked")
				Collection<Object> list = (Collection<Object>) ReflectionUtils
					.invokeMethod(propertyDescriptor.getReadMethod(), instance);
				if (list != null) {
					@SuppressWarnings("unchecked")
					Iterable<Object> messages = (Iterable<Object>) message
						.getField(message.getDescriptorForType().findFieldByName(propertyName));
					for (var item : messages) {
						if (item instanceof AbstractMessage) {
							FieldDescriptor field = message.getDescriptorForType().findFieldByName(propertyName);
							item = convert((AbstractMessage) item, field.getMessageType());
						}
						else {
							list.add(item);
						}
					}
				}
			}
			else if (Map.class.isAssignableFrom(propertyDescriptor.getPropertyType())) {
				@SuppressWarnings("unchecked")
				Map<Object, Object> map = (Map<Object, Object>) ReflectionUtils
					.invokeMethod(propertyDescriptor.getReadMethod(), instance);
				if (map != null) {
					@SuppressWarnings("unchecked")
					Iterable<DynamicMessage> messages = (Iterable<DynamicMessage>) message
						.getField(message.getDescriptorForType().findFieldByName(propertyName));
					for (var entryMessage : messages) {
						Object key = entryMessage.getField(entryMessage.getDescriptorForType().findFieldByName("key"));
						Object value = entryMessage
							.getField(entryMessage.getDescriptorForType().findFieldByName("value"));
						if (value instanceof AbstractMessage) {
							FieldDescriptor valueField = message.getDescriptorForType().findFieldByName(propertyName);
							value = convert((AbstractMessage) value, valueField.getMessageType());
						}
						map.put(key, value);
					}
				}
			}
			if (propertyDescriptor.getWriteMethod() == null) {
				continue;
			}
			FieldDescriptor field = message.getDescriptorForType().findFieldByName(propertyName);
			if (field != null && message.hasField(field)) {
				Object value = message.getField(message.getDescriptorForType().findFieldByName(propertyName));
				if (value instanceof AbstractMessage) {
					value = convert((AbstractMessage) value, propertyDescriptor.getPropertyType());
				}
				Method method = propertyDescriptor.getWriteMethod();
				ReflectionUtils.makeAccessible(method);
				ReflectionUtils.invokeMethod(method, instance, value);
			}
		}
		return instance;
	}

	public <T> AbstractMessage convert(T value, Descriptor descriptor) {
		if (value == null) {
			return DynamicMessage.newBuilder(DescriptorMapper.DEFAULT_INSTANCE.descriptor(Void.class)).build();
		}
		if (value instanceof AbstractMessage) {
			return (AbstractMessage) value;
		}
		if (descriptor == null) {
			throw new IllegalArgumentException("No descriptor found for class: " + value.getClass());
		}
		Builder builder = DynamicMessage.newBuilder(descriptor);
		if (value instanceof Map.Entry<?, ?> entry) {
			FieldDescriptor keyDescriptor = descriptor.findFieldByName("key");
			builder.setField(keyDescriptor, entry.getKey());
			FieldDescriptor valueDescriptor = descriptor.findFieldByName("value");
			FieldDescriptor.Type valueType = valueDescriptor.getType();
			if (valueType == FieldDescriptor.Type.MESSAGE) {
				AbstractMessage message = convert(entry.getValue(), valueDescriptor.getMessageType());
				builder.setField(descriptor.findFieldByName("value"), message);
			}
			else {
				builder.setField(descriptor.findFieldByName("value"), entry.getValue());
			}
			return builder.build();
		}
		for (PropertyDescriptor propertyDescriptor : BeanUtils.getPropertyDescriptors(value.getClass())) {
			String propertyName = propertyDescriptor.getName();
			FieldDescriptor field = descriptor.findFieldByName(propertyName);
			if (field != null) {
				Method method = propertyDescriptor.getReadMethod();
				ReflectionUtils.makeAccessible(method);
				if (field.getType() == FieldDescriptor.Type.MESSAGE) {
					Object nestedValue = ReflectionUtils.invokeMethod(method, value);
					if (field.getMessageType().getOptions().getMapEntry()) {
						// It's a map
						@SuppressWarnings("unchecked")
						var map = (Map<Object, Object>) nestedValue;
						for (var entry : map.entrySet()) {
							var entryMessage = convert(entry, field.getMessageType());
							builder.addRepeatedField(field, entryMessage);
						}
					}
					else {
						AbstractMessage nestedMessage = convert(nestedValue, field.getMessageType());
						builder.setField(field, nestedMessage);
					}
				}
				else {
					Object fieldValue = ReflectionUtils.invokeMethod(method, value);
					builder.setField(field, fieldValue);
				}
			}
		}
		return builder.build();
	}

}
