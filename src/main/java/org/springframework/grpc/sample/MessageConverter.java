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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;

import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DynamicMessage.Builder;

public class MessageConverter {

	private final DescriptorProvider descriptors;
	public MessageConverter(DescriptorProvider descriptors) {
		this.descriptors = descriptors;
	}

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

	public <T> AbstractMessage convert(T value) {
		if (value == null) {
			if (this.descriptors.descriptor(Void.class) != null) {
				return DynamicMessage.newBuilder(this.descriptors.descriptor(Void.class)).build();
			}
			return null;
		}
		if (value instanceof AbstractMessage) {
			return (AbstractMessage) value;
		}
		Descriptor descriptor = this.descriptors.descriptor(value.getClass());
		if (descriptor == null) {
			throw new IllegalArgumentException("No descriptor found for class: " + value.getClass());
		}
		Builder builder = DynamicMessage.newBuilder(descriptor);
		for (PropertyDescriptor propertyDescriptor : BeanUtils.getPropertyDescriptors(value.getClass())) {
			String propertyName = propertyDescriptor.getName();
			FieldDescriptor field = descriptor.findFieldByName(propertyName);
			if (field != null) {
				Method method = propertyDescriptor.getReadMethod();
				ReflectionUtils.makeAccessible(method);
				if (field.getType() == FieldDescriptor.Type.MESSAGE) {
					Object nestedValue = ReflectionUtils.invokeMethod(method, value);
					AbstractMessage nestedMessage = convert(nestedValue);
					builder.setField(field, nestedMessage);
				} else {
					Object fieldValue = ReflectionUtils.invokeMethod(method, value);
					builder.setField(field, fieldValue);
				}
			}
		}
		return builder.build();
	}
}
