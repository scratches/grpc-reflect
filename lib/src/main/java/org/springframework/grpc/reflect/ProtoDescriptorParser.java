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
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.grpc.parser.DefaultPathLocator;
import org.springframework.grpc.parser.FileDescriptorProtoParser;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

/**
 * {@link DescriptorParser} implementation that reads Protobuf source files
 * ({@code .proto}) at runtime using {@link FileDescriptorProtoParser}.
 * <p>
 * This allows import resolution across multiple {@code .proto} files without requiring a
 * separate {@code protoc} invocation.
 */
public class ProtoDescriptorParser implements DescriptorParser, ResourceLoaderAware {

	private PathMatchingResourcePatternResolver resourceLoader = new PathMatchingResourcePatternResolver(
			new DefaultResourceLoader());

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = new PathMatchingResourcePatternResolver(resourceLoader);
	}

	@Override
	public FileDescriptorSet resolve(String base, String... locations) {
		if (base == null) {
			base = "";
		}
		String bare = base;
		if (bare.contains(":")) {
			while (bare.endsWith("/")) {
				bare = bare.substring(0, bare.length() - 1);
			}
			bare = bare.substring(bare.indexOf(':') + 1);
		}
		FileDescriptorProtoParser fds = new FileDescriptorProtoParser();
		fds.setPathLocator(new DefaultPathLocator(bare));
		List<String> paths = new ArrayList<>();
		for (String location : locations) {
			boolean hasBase = false;
			if (base.length() > 0) {
				if (!location.contains(":") && !location.startsWith("/")) {
					location = base + (base.endsWith("/") ? "" : "/") + location;
					hasBase = true;
				}
			}
			String rootDir = determineRootDir(location);
			Resource[] resources;
			try {
				resources = resourceLoader.getResources(location);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to find resources for location: " + location, e);
			}
			for (Resource resource : resources) {
				if (resource.exists()) {
					String url;
					try {
						url = resource.getURL().getPath();
					}
					catch (IOException e) {
						throw new IllegalStateException("Failed to get URL for resource: " + resource, e);
					}
					url = url.substring(url.lastIndexOf(rootDir));
					if (hasBase && url.startsWith(bare)) {
						url = url.substring(bare.length());
					}
					if (url.startsWith("/") && (resource instanceof ClassPathResource || bare.length() > 0)) {
						url = url.substring(1);
					}
					paths.add(url);
				}
				else {
					throw new IllegalArgumentException("Resource does not exist: " + resource);
				}
			}
		}
		return fds.resolve(paths.toArray(new String[0]));
	}

	private String determineRootDir(String location) {
		if (location.contains(":")) {
			location = location.substring(location.indexOf(':') + 1);
		}
		if (!this.resourceLoader.getPathMatcher().isPattern(location)) {
			return location;
		}
		int rootDirEnd = location.length();
		while (rootDirEnd > 0 && this.resourceLoader.getPathMatcher().isPattern(location.substring(0, rootDirEnd))) {
			rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
		}
		if (rootDirEnd < 0) {
			rootDirEnd = 0;
		}
		return location.substring(0, rootDirEnd);
	}

}
