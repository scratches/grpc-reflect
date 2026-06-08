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

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

/**
 * Strategy for resolving a set of Protobuf file descriptors from one or more resource
 * locations. Implementations may differ in the format they accept (e.g. pre-compiled
 * {@code .pb} binary files vs. raw {@code .proto} source files).
 *
 * @see BinaryDescriptorParser
 * @see ProtoDescriptorParser
 */
public interface DescriptorParser {

	/**
	 * Resolve all Protobuf file descriptors from the given locations. Implementations
	 * should skip any duplicate descriptors (e.g. if the same file is found in multiple
	 * locations) and should ignore any resources that cannot be resolved or parsed (e.g.
	 * by using a file name extension convention).
	 * @param base a base path or resource prefix prepended to relative locations; may be
	 * {@code null} or empty to use locations as-is
	 * @param resources one or more resource location patterns pointing to descriptor
	 * files or directories containing them
	 * @return a {@link FileDescriptorSet} containing all resolved descriptors
	 */
	FileDescriptorSet resolve(String base, String... resources);

}
