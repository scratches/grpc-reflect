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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

public interface DescriptorProtoProvider {

	DescriptorProto proto(Class<?> clazz);

	public DescriptorProtoProvider DEFAULT_INSTANCE = clazz -> {
		DescriptorProto.Builder builder = DescriptorProto.newBuilder();
		builder.setName(clazz.getSimpleName());
		if (clazz == Void.class || clazz == Void.TYPE) {
			return builder.build();
		}
		int count = 1;
		for (var field : clazz.getDeclaredFields()) {
			Type type = findType(field.getType());
			DescriptorProtos.FieldDescriptorProto.Builder fb = DescriptorProtos.FieldDescriptorProto.newBuilder().setName(field.getName())
			.setNumber(count).setType(type);
			if (type == Type.TYPE_MESSAGE) {
				fb.setTypeName(field.getType().getSimpleName());
			}
			builder.addField(fb.build());
			count++;
		}
		return builder.build();
	};

	private static DescriptorProtos.FieldDescriptorProto.Type findType(Class<?> type) {
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
		return DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
	}
	
}
