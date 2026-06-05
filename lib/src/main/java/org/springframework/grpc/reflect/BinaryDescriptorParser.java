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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class BinaryDescriptorParser implements DescriptorParser {

	@Override
	public FileDescriptorSet resolve(Resource... resources) {
		Set<String> files = new HashSet<>();
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		for (Resource resource : resources) {
			try {
				String suffix = StringUtils.getFilenameExtension(resource.getFilename());
				if (suffix == null || !suffix.equals("pb")) {
					continue;
				}
				if (!resource.exists() || !resource.getFilename().endsWith(".pb")) {
					continue;
				}
				FileDescriptorSet proto = FileDescriptorSet.parseFrom(resource.getInputStream());
				for (FileDescriptorProto resolved : proto.getFileList()) {
					if (files.contains(resolved.getName())) {
						continue;
					}
					files.add(resolved.getName());
					builder.addFile(resolved);
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to read file: " + resource, e);
			}
		}
		return builder.build();
	}

}
