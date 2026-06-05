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

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

public class BinaryDescriptorParser implements DescriptorParser, ResourceLoaderAware {

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public FileDescriptorSet resolve(String... inputs) {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(this.resourceLoader);
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		Set<String> files = new HashSet<>();
		for (String input : inputs) {
			try {
				String path = input;
				// Directory search
				if (!path.endsWith(".pb")) {
					if (path.endsWith("/")) {
						path = path.substring(0, path.length()-1);
					}
					if (!path.contains("*")) {
						path = path + "/**/*.pb";
					} else {
						path = path + "/*.pb";
					}
				}
				String suffix = StringUtils.getFilenameExtension(path);
				if (suffix == null || !suffix.equals("pb")) {
					continue;
				}
				Resource[] resources = resolver.getResources(path);
				for (Resource resource : resources) {
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
				}
			} catch (IOException e) {
				throw new IllegalStateException("Failed to read file: " + input, e);
			}
		}
		return builder.build();
	}

}
