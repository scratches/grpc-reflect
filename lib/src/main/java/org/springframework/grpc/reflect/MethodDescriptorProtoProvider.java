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

import java.lang.reflect.Method;

import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;

public interface MethodDescriptorProtoProvider {

	MethodDescriptorProto service(Method method);

	static MethodDescriptorProtoProvider DEFAULT_INSTANCE = method -> {
		if (method.getParameterTypes().length != 1) {
			throw new IllegalArgumentException("Method must have exactly one parameter");
		}
		if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
			throw new IllegalArgumentException("Method must have a return type");
		}
		String inputType = method.getParameterTypes()[0].getSimpleName();
		String outputType = method.getReturnType().getSimpleName();
		return MethodDescriptorProto.newBuilder()
				.setName(StringUtils.capitalize(method.getName()))
				.setInputType(inputType)
				.setOutputType(outputType)
				.build();
	};
}
