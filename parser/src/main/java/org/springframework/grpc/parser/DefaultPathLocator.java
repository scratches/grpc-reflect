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
package org.springframework.grpc.parser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class DefaultPathLocator implements PathLocator {

	private String base;

	public DefaultPathLocator(String base) {
		if (base == null) {
			base = "";
		}
		if (!base.isEmpty() && !base.endsWith("/")) {
			base += "/";
		}
		this.base = base;
	}

	public DefaultPathLocator() {
		this("");
	}

	@Override
	public NamedBytes[] find(String pattern) {
		Path base = Path.of(this.base);
		Path path = Path.of(pattern);
		Path input = path.isAbsolute() ? path : base.resolve(path);
		if (!input.toFile().exists()) {
			try {
				Enumeration<URL> resources = getClass().getClassLoader()
					.getResources(input.toString().replace("\\", "/"));
				if (!resources.hasMoreElements()) {
					throw new IllegalArgumentException("Input file does not exist: " + input);
				}
				URL url = resources.nextElement();
				return new NamedBytes[] { new NamedBytes(pattern, () -> {
					try (InputStream stream = url.openStream()) {
						return stream.readAllBytes();
					}
					catch (IOException e) {
						throw new IllegalStateException("Failed to read resource: " + input, e);
					}
				}) };
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to read input file: " + input, e);
			}
		}
		if (!Files.isDirectory(input) && !input.toFile().exists()) {
			throw new IllegalArgumentException("Input file does not exist: " + input);
		}
		Set<String> names = new HashSet<>();
		Set<NamedBytes> result = new HashSet<>();
		if (input.toFile().isDirectory()) {
			try {
				Files.walk(input).filter(file -> !Files.isDirectory(file)).forEach(file -> {
					String name = base.relativize(file.normalize()).toString();
					if (!names.contains(name)) {
						result.add(new NamedBytes(name, () -> {
							try {
								return Files.readAllBytes(file);
							}
							catch (IOException e) {
								throw new IllegalStateException("Failed to read file: " + file, e);
							}
						}));
						// Avoid duplicates
						names.add(name);
					}
				});
				return result.toArray(new NamedBytes[0]);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to read input directory: " + input, e);
			}
		}
		return new NamedBytes[] { new NamedBytes(base.relativize(input).toString(), () -> {
			try {
				return Files.readAllBytes(input);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to read file: " + input, e);
			}
		}) };
	}

}