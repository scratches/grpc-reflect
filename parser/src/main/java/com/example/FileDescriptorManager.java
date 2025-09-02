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
package com.example;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.ProtocolStringList;

/**
 * The {@code FileDescriptorManager} class provides functionality to convert
 * {@link FileDescriptorProto} into {@link FileDescriptor} objects. It does not resolve
 * dependencies between file descriptors within the set.
 *
 * <p>
 * <strong>Usage:</strong>
 * </p>
 * <pre>
 * FileDescriptorManager manager = new FileDescriptorManager();
 * FileDescriptor[] descriptors = manager.convert(fileDescriptorSet);
 * </pre>
 */
public class FileDescriptorManager {

	public FileDescriptor[] convert(FileDescriptorSet input) {
		FileDescriptor[] output = new FileDescriptor[input.getFileCount()];
		for (int i = 0; i < input.getFileCount(); i++) {
			try {
				FileDescriptorProto file = input.getFile(i);
				FileDescriptor fd = FileDescriptor.buildFrom(file, dependencies(input, file.getDependencyList()));
				output[i] = fd;
			}
			catch (DescriptorValidationException e) {
				throw new IllegalStateException("Invalid descriptor: " + input.getFile(i).getName(), e);
			}
		}
		return output;
	}

	private FileDescriptor[] dependencies(FileDescriptorSet input, ProtocolStringList list) {
		FileDescriptor[] deps = new FileDescriptor[list.size()];
		for (int i = 0; i < list.size(); i++) {
			String name = list.get(i);
			FileDescriptorProto file = findFileByName(input, name);
			if (file == null) {
				throw new IllegalStateException("Missing dependency: " + name);
			}
			try {
				deps[i] = FileDescriptor.buildFrom(file, dependencies(input, file.getDependencyList()));
			}
			catch (DescriptorValidationException e) {
				throw new IllegalStateException("Invalid descriptor: " + file.getName(), e);
			}
		}
		return deps;
	}

	private FileDescriptorProto findFileByName(FileDescriptorSet input, String name) {
		for (FileDescriptorProto file : input.getFileList()) {
			if (file.getName().equals(name)) {
				return file;
			}
		}
		return null;
	}

}
