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

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

/**
 * {@link DescriptorParser} implementation that reads pre-compiled Protobuf binary
 * descriptor files ({@code .pb} files produced by {@code protoc --descriptor_set_out}).
 * <p>
 * If a location does not already end in {@code .pb} it is treated as a directory and
 * {@code /**&#47;*.pb} is appended automatically. Files are deduplicated by their
 * {@link com.google.protobuf.DescriptorProtos.FileDescriptorProto#getName() name} so that
 * the same descriptor is never added twice.
 */
public class BinaryDescriptorParser implements DescriptorParser, ResourceLoaderAware {

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public FileDescriptorSet resolve(@Nullable String base, String... locations) {
		Set<String> files = new HashSet<>();
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		if (base == null) {
			base = "";
		}
		for (String location : locations) {
			String ext = StringUtils.getFilenameExtension(location);
			if (ext != null && !ext.equals("pb")) {
				continue;
			}
			if (base.length() > 0) {
				if (!location.contains(":") && !location.startsWith("/")) {
					location = base + (base.endsWith("/") ? "" : "/") + location;
				}
			}
			if (!location.endsWith(".pb")) {
				if (location.endsWith("/")) {
					location = location.substring(0, location.length() - 1);
				}
				if (!location.contains("*")) {
					location = location + "/**/*.pb";
				}
				else {
					location = location + "/*.pb";
				}
			}
			Resource[] resources;
			try {
				resources = resolver.getResources(location);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to find resources for location: " + location, e);
			}
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
				}
				catch (IOException e) {
					throw new IllegalStateException("Failed to read file: " + resource, e);
				}
			}
		}
		return builder.build();
	}

}
