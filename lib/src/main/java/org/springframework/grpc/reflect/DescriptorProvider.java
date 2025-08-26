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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.util.ReflectionUtils;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

public interface DescriptorProvider {
	Descriptor descriptor(Class<?> clazz);

	static DescriptorProvider DEFAULT_INSTANCE = new DescriptorProvider() {
		private DescriptorProtoExtractor extractor = DescriptorProtoExtractor.DEFAULT_INSTANCE;
		private Map<Class<?>, Descriptor> cache = new HashMap<>();

		@Override
		public Descriptor descriptor(Class<?> clazz) {
			if (clazz == null) {
				clazz = Void.class;
			}
			if (this.cache.containsKey(clazz)) {
				return this.cache.get(clazz);
			}
			DescriptorProto message = extractor.proto(clazz);
			FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
			builder.addMessageType(message);
			try {
				Set<FileDescriptor> dependencies = new HashSet<>();
				for (var field : message.getFieldList()) {
					if (field.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE) {
						Class<?> fieldType = ReflectionUtils.findField(clazz, field.getName()).getType();
						if (fieldType != null && fieldType != clazz) {
							dependencies.add(descriptor(fieldType).getFile());
						}
					}
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
	};
}
