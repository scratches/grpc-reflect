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
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.grpc.parser.PathLocator;

public class ResourceLoaderPathLocator implements PathLocator {

	private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	@Override
	public Path[] find(String path) {
		Resource[] resources;
		try {
			resources = resolver.getResources("classpath*:" + path + "/**/*.proto");
			if (resources.length == 0) {
				resources = resolver.getResources("classpath*:" + path + "/**/*.pb");
				if (resources.length == 0) {
					return new Path[0];
				}
			}
			Path[] urls = new Path[resources.length];
			for (int i = 0; i < resources.length; i++) {
				try {
					String url = resources[i].getURL().toString();
					url = url.substring(url.lastIndexOf(path)); // Remove the path prefix
					urls[i] = Path.of(url);
				} catch (IOException e) {
					throw new IllegalStateException("Failed to get URL for resource: " + resources[i], e);
				}
			}
			return urls;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to get URL for path: " + path, e);
		}
	}

}
