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

package org.springframework.grpc.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.springframework.grpc.parser.support.ProtoParserV2;
import org.springframework.grpc.parser.support.ProtoParserV3;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;

/**
 * A parser for Protocol Buffers (.proto) files that can parse, resolve
 * dependencies, and
 * A parser for Protocol Buffers (.proto) files that can parse, resolve
 * dependencies, and
 * build {@link FileDescriptorProto} and {@link FileDescriptorSet} objects.
 *
 * <p>
 * This provides methods to parse Protocol Buffers definitions from strings,
 * input
 * streams, and file paths. It also resolves dependencies between .proto files
 * and builds
 * a complete {@link FileDescriptorSet} that includes all required files.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Parses .proto files into {@link FileDescriptorProto} objects.</li>
 * <li>Resolves dependencies between .proto files.</li>
 * <li>Supports parsing from strings, input streams, and file paths.</li>
 * <li>Handles imports and package definitions in .proto files.</li>
 * <li>Maintains a cache of parsed files to avoid redundant parsing.</li>
 * </ul>
 *
 * <p>
 * Usage:
 *
 * <pre>
 * {@code
 * FileDescriptorProtoParser parser = new FileDescriptorProtoParser();
 * FileDescriptorSet descriptorSet = parser.resolve(Paths.get("example.proto"));
 * }
 * </pre>
 *
 * <p>
 * Note: This parser assumes the use of the "proto3" syntax and does not support
 * "proto2".
 *
 * <p>
 * Thread Safety: This class is not thread-safe. If multiple threads need to use
 * the
 * parser, external synchronization is required.
 *
 * <p>
 * Limitations: Assumes all imports are either available in the classpath or in
 * the
 * specified base path.
 *
 * <p>
 * Example:
 *
 * <pre>
 * {@code
 * Path basePath = Paths.get("/path/to/protos");
 * FileDescriptorProtoParser parser = new FileDescriptorProtoParser(basePath);
 * FileDescriptorSet descriptorSet = parser.resolve("example.proto");
 * }
 * </pre>
 *
 * @author Dave Syer
 */
public class FileDescriptorProtoParser {

	private final ProtoParserV3 v3;

	private final ProtoParserV2 v2;

	private Map<String, FileDescriptorProto> cache = new HashMap<>();

	private final Path base;

	private PathLocator locator = null;

	/**
	 * Constructs a new {@code FileDescriptorProtoParser} with a default path. This
	 * constructor initializes the parser with an empty path.
	 */
	public FileDescriptorProtoParser() {
		this(Path.of(""));
	}

	/**
	 * Constructs a new {@code FileDescriptorProtoParser} with the specified base
	 * path.
	 * Imports in .proto files will be resolved relative to this base path and paths
	 * to
	 * .proto files will be resolved relative to this base path as well.
	 * 
	 * @param base the base path to be used by the parser
	 */
	public FileDescriptorProtoParser(Path base) {
		this.base = base;
		this.v3 = new ProtoParserV3();
		this.v2 = new ProtoParserV2();
	}

	/**
	 * Sets a custom {@link PathLocator} to resolve import paths. If not set, the
	 * parser only resolve from the filesystem or individual resources (not
	 * directories)
	 * in the classpath.
	 * 
	 * @param locator the {@link PathLocator} to use for resolving import paths
	 */
	public void setPathLocator(PathLocator locator) {
		this.locator = locator;
	}

	/**
	 * Resolves a set of {@link FileDescriptorProto} inputs into a
	 * {@link FileDescriptorSet}. This method processes each input, ensuring that
	 * all
	 * dependencies are resolved and added to the resulting
	 * {@link FileDescriptorSet}.
	 * 
	 * @param inputs an array of {@link FileDescriptorProto} objects to be resolved
	 * @return a {@link FileDescriptorSet} containing the resolved descriptors
	 * @throws IllegalArgumentException if the there are unresolved dependencies
	 */
	public FileDescriptorSet resolve(FileDescriptorProto... inputs) {
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		Set<String> names = new HashSet<>();
		for (FileDescriptorProto input : inputs) {
			resolve(builder, input, names);
		}
		return builder.build();
	}

	/**
	 * Resolves a {@link FileDescriptorSet} from the given input stream.
	 * Dependencies are
	 * resolved from the classpath or relative to the base path.
	 * 
	 * @param name  the name associated with the input stream, used for parsing.
	 * @param input the input stream containing the data to be parsed.
	 * @return a {@link FileDescriptorSet} resolved from the parsed
	 *         {@link FileDescriptorProto}.
	 * @throws IllegalArgumentException if the input is not a valid .proto file or
	 *                                  if it
	 *                                  contains unresolved dependencies
	 * @throws IllegalStateException    if an I/O error occurs while reading the
	 *                                  input
	 *                                  stream.
	 */
	public FileDescriptorSet resolve(String name, InputStream input) {
		FileDescriptorProto proto = parse(name, input);
		return resolve(proto);
	}

	/**
	 * Resolves a {@link FileDescriptorSet} from the given input string. The input
	 * is a
	 * single .proto files in "proto3" syntax, but if it contains imports, those
	 * will be
	 * resolved. Dependencies are resolved from the classpath or relative to the
	 * base
	 * path.
	 * 
	 * @param name  the name associated with the input, typically used for error
	 *              reporting
	 * @param input the input string containing the protocol buffer definition
	 * @return a {@link FileDescriptorSet} representing the resolved protocol buffer
	 *         definitions
	 * @throws IllegalArgumentException if the input is not a valid .proto file or
	 *                                  if it
	 *                                  contains unresolved dependencies
	 * @throws IllegalStateException    if an error occurs during parsing
	 */
	public FileDescriptorSet resolve(String name, String input) {
		FileDescriptorProto proto = parse(name, new ByteArrayInputStream(input.getBytes()));
		return resolve(proto);
	}

	/**
	 * Resolves the provided input paths into a {@link FileDescriptorSet}. This
	 * method
	 * parses each input path, relative to the base path, extracts the file
	 * descriptors,
	 * and aggregates them into a single {@link FileDescriptorSet}.
	 *
	 * Dependencies are resolved from the classpath or relative to the base path.
	 * 
	 * @param inputs an array of {@link Path} objects representing the input files
	 *               to
	 *               parse
	 * @return a {@link FileDescriptorSet} containing all the file descriptors from
	 *         the
	 *         provided inputs
	 * @throws IllegalArgumentException if the inputs are not valid .proto files or
	 *                                  if
	 *                                  they contains unresolved dependencies
	 */
	public FileDescriptorSet resolve(Path... inputs) {
		FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
		for (Path input : inputs) {
			parse(input).getFileList().forEach(builder::addFile);
		}
		return builder.build();
	}

	private void resolve(FileDescriptorSet.Builder builder, FileDescriptorProto proto, Set<String> names) {
		if (names.contains(proto.getName())) {
			return; // Already processed
		}
		for (String name : proto.getDependencyList()) {
			if (names.contains(name)) {
				continue; // Already processed
			}
			FileDescriptorProto dependency;
			if (cache.containsKey(name)) {
				dependency = cache.get(name);
			} else {
				try (InputStream stream = findImport(name)) {
					dependency = parse(name, stream);
				} catch (IOException e) {
					throw new IllegalStateException("Failed to read import: " + name, e);
				}
			}
			resolve(builder, dependency, names);
		}
		builder.addFile(proto);
		names.add(proto.getName());
	}

	private InputStream findImport(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
		if (stream == null) {
			if (base.resolve(path).toFile().exists()) {
				try {
					stream = Files.newInputStream(base.resolve(path));
				} catch (IOException e) {
					throw new IllegalStateException("Failed to read import: " + path, e);
				}
			} else {
				throw new IllegalArgumentException("Import not found: " + path);
			}
		}
		return stream;
	}

	private FileDescriptorSet parse(Path path) {
		Path input = path.isAbsolute() ? path : base.resolve(path);
		try {
			if (!input.toFile().exists()) {
				Enumeration<URL> resources = getClass().getClassLoader().getResources(input.toString());
				if (!resources.hasMoreElements()) {
					throw new IllegalArgumentException("Input file does not exist: " + input);
				}
				if (input.toString().endsWith(".proto")) {
					URL url = resources.nextElement();
					try (InputStream stream = url.openStream()) {
						FileDescriptorProto proto = parse(path.toString(), stream);
						return resolve(proto);
					} catch (IOException e) {
						throw new IllegalStateException("Failed to read resource: " + input, e);
					}
				}
				if (input.toString().endsWith(".pb")) {
					URL url = resources.nextElement();
					try (InputStream stream = url.openStream()) {
						FileDescriptorSet proto = FileDescriptorSet.parseFrom(stream);
						return proto;
					} catch (IOException e) {
						throw new IllegalStateException("Failed to read resource: " + input, e);
					}
				}
				if (this.locator != null) {
					Path[] urls = this.locator.find(input.toString());
					Set<Path> bases = resolveBase(this.base);
					if (!bases.isEmpty()) {
						for (int i = 0; i < urls.length; i++) {
							for (Path base : bases) {
								if (urls[i].startsWith(base)) {
									urls[i] = base.relativize(urls[i]);
									break;
								}
							}
						}
					}
					return resolve(urls);
				}
			}
			if (!Files.isDirectory(input) && !input.toString().endsWith(".proto")
					&& !input.toString().endsWith(".pb")) {
				throw new IllegalArgumentException("Input file is not .proto or .pb: " + input);
			}
			FileDescriptorSet.Builder builder = FileDescriptorSet.newBuilder();
			Set<String> names = new HashSet<>();
			if (input.toFile().isDirectory()) {
				Files.walk(input)
						.filter(file -> !Files.isDirectory(file) && file.toString().endsWith(".proto"))
						.forEach(file -> {
							try {
								Path name = base.relativize(file.normalize());
								FileDescriptorProto proto = parse(name.toString(), Files.newInputStream(file));
								for (FileDescriptorProto resolved : resolve(proto).getFileList()) {
									if (!names.contains(resolved.getName())) {
										// Avoid duplicates
										builder.addFile(resolved);
										names.add(resolved.getName());
									}
								}
							} catch (IOException e) {
								throw new IllegalStateException("Failed to read file: " + file, e);
							}
						});
				return builder.build();
			}
			if (input.toString().endsWith(".pb")) {
				return FileDescriptorSet.parseFrom(Files.newInputStream(input));
			}
			FileDescriptorProto proto = parse(path.toString(), Files.newInputStream(input));
			for (FileDescriptorProto resolved : resolve(proto).getFileList()) {
				builder.addFile(resolved);
			}
			return builder.build();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input file: " + input, e);
		}
	}

	private Set<Path> resolveBase(Path base) {
		Set<Path> bases = new HashSet<>();
		bases.add(base);
		if (this.locator != null) {
			for (Path extra : this.locator.find(base.toString())) {
				if (extra.getFileName().toString().endsWith(".proto") ||
						extra.getFileName().toString().endsWith(".pb")) {
					extra = extra.getParent();
				}
				bases.add(extra);
			}
		}
		return bases;

	}

	private FileDescriptorProto parse(String name, InputStream stream) {
		try (stream) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			stream.transferTo(out);
			String content = out.toString();
			if (content.matches("(?s).*syntax\\s*=\\s*\"proto2\".*")) {
				return v2.parse(name, CharStreams.fromString(content), path -> resolve(path, findImport(path)));
			}
			return v3.parse(name, CharStreams.fromString(content), path -> resolve(path, findImport(path)));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read import: " + name, e);
		}
	}

}
