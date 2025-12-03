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

import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;

/**
 * A catalog that manages and provides access to protocol buffer file descriptors.
 * <p>
 * This class serves as a central repository for storing and retrieving gRPC descriptors
 * used in reflection.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class DescriptorCatalog implements DescriptorProvider, DescriptorRegistry {

	private Map<String, FileDescriptor> fileDescriptors = new HashMap<>();
	private Map<String, Descriptor> descriptors = new HashMap<>();

	@Override
	public void register(FileDescriptor file) {
		for (ServiceDescriptor service : file.getServices()) {
			register(service);
		}
		for (Descriptor type : file.getMessageTypes()) {
			register(type);
		}
	}
	
	@Override
	public void register(ServiceDescriptor service) {
		this.fileDescriptors.put(service.getFullName(), service.getFile());
	}
	
	private void register(Descriptor type) {
		this.descriptors.put(type.getFullName(), type);
	}

	private String pkg(FileDescriptor file) {
		String pkg = file.getPackage();
		if (pkg == null || pkg.isEmpty() || pkg.equals(".")) {
			pkg = "";
		}
		else {
			pkg = pkg + ".";
		}
		return pkg;
	}

	@Override
	public Descriptor type(String name) {
		if (this.descriptors.containsKey(name)) {
			return this.descriptors.get(name);
		}
		for (FileDescriptor file : this.fileDescriptors.values()) {
			String pkg = pkg(file);
			if (!pkg.isEmpty()) {
				if (name.startsWith(pkg)) {
					name = name.substring(pkg.length());
				}
				else {
					continue;
				}
			}
			Descriptor descriptor = file.findMessageTypeByName(name);
			if (descriptor != null) {
				return descriptor;
			}
		}
		return null;
	}

	@Override
	public ServiceDescriptor service(String name) {
		for (FileDescriptor file : this.fileDescriptors.values()) {
			String pkg = pkg(file);
			if (!pkg.isEmpty()) {
				if (name.startsWith(pkg)) {
					name = name.substring(pkg.length());
				}
				else {
					continue;
				}
			}
			ServiceDescriptor descriptor = file.findServiceByName(name);
			if (descriptor != null) {
				return descriptor;
			}
		}
		return null;
	}

}
