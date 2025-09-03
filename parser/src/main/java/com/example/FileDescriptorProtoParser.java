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

package com.example;

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
import java.util.Stack;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.example.ProtobufParser.EnumDefContext;
import com.example.ProtobufParser.EnumFieldContext;
import com.example.ProtobufParser.FieldContext;
import com.example.ProtobufParser.FieldLabelContext;
import com.example.ProtobufParser.ImportStatementContext;
import com.example.ProtobufParser.KeyTypeContext;
import com.example.ProtobufParser.MapFieldContext;
import com.example.ProtobufParser.PackageStatementContext;
import com.example.ProtobufParser.RpcContext;
import com.example.ProtobufParser.ServiceDefContext;
import com.example.ProtobufParser.TypeContext;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

/**
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

	private Map<String, FileDescriptorProto> cache = new HashMap<>();

	private Set<String> enumNames = new HashSet<>();

	private final Path base;

	private static final boolean IS_SPRING = FileDescriptorProtoParser.class.getClassLoader()
			.getResource("org/springframework/core/io/support/PathMatchingResourcePatternResolver.class") != null;

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
	}

	/**
	 * Parses the given input string into a FileDescriptorProto object.
	 *
	 * @see #resolve(String, String) for resolving dependencies
	 * @param name  the name associated with the input, typically used for error
	 *              reporting
	 * @param input the input string to be parsed, must be a single "proto3"
	 *              definition
	 * @return a FileDescriptorProto object representing the parsed input
	 */
	public FileDescriptorProto parse(String name, String input) {
		CharStream stream = CharStreams.fromString(input);
		return parse(name, stream);
	}

	/**
	 * Parses a protocol buffer descriptor from the given input stream.
	 *
	 * @see #resolve(String, InputStream) for resolving dependencies
	 * @param name  the name associated with the descriptor being parsed
	 * @param input the input stream containing the protocol buffer descriptor data
	 * @return the parsed {@link FileDescriptorProto} object
	 * @throws IllegalStateException if an I/O error occurs while reading the input
	 *                               stream
	 */
	public FileDescriptorProto parse(String name, InputStream input) {
		try {
			return parse(name, CharStreams.fromStream(input));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input stream: " + input, e);
		}
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
		try {
			FileDescriptorProto proto = parse(name, CharStreams.fromStream(input));
			return resolve(proto);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input stream: " + input, e);
		}
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
		CharStream stream = CharStreams.fromString(input);
		FileDescriptorProto proto = parse(name, stream);
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
					dependency = parse(name, CharStreams.fromStream(stream));
				} catch (IOException e) {
					throw new IllegalStateException("Failed to read import: " + name, e);
				}
			}
			resolve(builder, dependency, names);
		}
		builder.addFile(proto);
		names.add(proto.getName());
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
						FileDescriptorProto proto = parse(path.toString(), CharStreams.fromStream(stream));
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
				if (IS_SPRING) {
					// Use Spring's resource loader if available
					Path[] urls = findResources(input.toString());
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
								FileDescriptorProto proto = parse(name.toString(), CharStreams.fromPath(file));
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
			FileDescriptorProto proto = parse(path.toString(), CharStreams.fromPath(input));
			for (FileDescriptorProto resolved : resolve(proto).getFileList()) {
				builder.addFile(resolved);
			}
			return builder.build();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read input file: " + input, e);
		}
	}

	private Path[] findResources(String path) {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
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
					if (!base.toString().isEmpty() && url.startsWith(base.toString())) {
						url = url.substring(url.lastIndexOf(base.toString()) + base.toString().length() + 1);
					}
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

	private FileDescriptorProto parse(String name, CharStream stream) {

		if (cache.containsKey(name)) {
			return cache.get(name);
		}

		ProtobufLexer lexer = new ProtobufLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ProtobufParser parser = new ProtobufParser(tokens);

		FileDescriptorProto.Builder builder = FileDescriptorProto.newBuilder();
		builder.setName(name);
		builder.setSyntax("proto3");

		parser.removeErrorListeners(); // Remove default error listeners
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("Syntax error at line " + line + ": " + msg, e);
			}
		});
		Set<String> localEnumNames = new HashSet<>();
		parser.addParseListener(new ProtobufBaseListener() {
			private String packageName;

			@Override
			public void exitPackageStatement(PackageStatementContext ctx) {
				String packageName = ctx.fullIdent().getText();
				if (!packageName.isEmpty()) {
					this.packageName = packageName;
				}
			}

			@Override
			public void exitEnumDef(EnumDefContext ctx) {
				localEnumNames.add(ctx.enumName().getText());
				if (this.packageName != null) {
					enumNames.add(this.packageName + "." + ctx.enumName().getText());
				} else {
					enumNames.add(ctx.enumName().getText());
				}
			}
		});

		parser.proto().accept(new ProtobufBaseVisitor<>() {
			@Override
			public Object visitImportStatement(ImportStatementContext ctx) {
				String path = ctx.strLit().getText();
				path = path.replace("\"", "").replace("'", "");
				if (!cache.containsKey(path)) {
					parse(path, findImport(path));
				}
				return super.visitImportStatement(ctx);
			}
		});
		parser.reset();
		FileDescriptorProto proto = parser.proto().accept(new ProtobufDescriptorVisitor(builder, localEnumNames))
				.build();
		cache.put(name, proto);
		return proto;
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

	class ProtobufDescriptorVisitor extends ProtobufBaseVisitor<FileDescriptorProto.Builder> {

		private final FileDescriptorProto.Builder builder;

		private Stack<DescriptorProto.Builder> type = new Stack<>();

		private Stack<EnumDescriptorProto.Builder> enumType = new Stack<>();

		private Stack<FieldDescriptorProto.Builder> field = new Stack<>();

		private final Set<String> localEnumNames;

		public ProtobufDescriptorVisitor(FileDescriptorProto.Builder builder, Set<String> localEnumNames) {
			this.builder = builder;
			this.localEnumNames = localEnumNames;
		}

		@Override
		protected FileDescriptorProto.Builder defaultResult() {
			return builder;
		}

		@Override
		public Builder visitPackageStatement(PackageStatementContext ctx) {
			String packageName = ctx.fullIdent().getText();
			if (!packageName.isEmpty()) {
				builder.setPackage(packageName);
			}
			return super.visitPackageStatement(ctx);
		}

		@Override
		public FileDescriptorProto.Builder visitFieldLabel(FieldLabelContext ctx) {
			this.field.peek().setLabel(findLabel(ctx));
			return super.visitFieldLabel(ctx);
		}

		private FieldDescriptorProto.Label findLabel(FieldLabelContext ctx) {
			if (ctx.OPTIONAL() != null) {
				return FieldDescriptorProto.Label.LABEL_OPTIONAL;
			}
			if (ctx.REPEATED() != null) {
				return FieldDescriptorProto.Label.LABEL_REPEATED;
			}
			throw new IllegalStateException("Unknown field label: " + ctx.getText());
		}

		@Override
		public FileDescriptorProto.Builder visitField(FieldContext ctx) {
			// TODO: handle field options if needed
			FieldDescriptorProto.Type fieldType = findType(ctx.type());
			FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
					.setName(ctx.fieldName().getText())
					.setNumber(Integer.valueOf(ctx.fieldNumber().getText()))
					.setType(fieldType);
			this.field.push(field);
			if (fieldType == FieldDescriptorProto.Type.TYPE_MESSAGE
					|| fieldType == FieldDescriptorProto.Type.TYPE_ENUM) {
				field.setTypeName(ctx.type().messageType().getText());
			}
			FileDescriptorProto.Builder result = super.visitField(ctx);
			this.type.peek().addField(field.build());
			this.field.pop();
			return result;
		}

		@Override
		public Builder visitMapField(MapFieldContext ctx) {
			FieldDescriptorProto.Type fieldType = FieldDescriptorProto.Type.TYPE_MESSAGE;
			DescriptorProto mapType = mapType(ctx);
			FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
					.setName(ctx.mapName().getText())
					.setNumber(Integer.valueOf(ctx.fieldNumber().getText()))
					.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
					.setTypeName(mapType.getName())
					.setType(fieldType);
			this.field.push(field);
			FileDescriptorProto.Builder result = super.visitMapField(ctx);
			this.type.peek().addNestedType(mapType);
			this.type.peek().addField(field.build());
			this.field.pop();
			return result;
		}

		private DescriptorProto mapType(MapFieldContext ctx) {
			DescriptorProto.Builder type = DescriptorProto.newBuilder()
					.setName(capitalize(ctx.mapName().getText()) + "Entry");
			FieldDescriptorProto.Builder key = FieldDescriptorProto.newBuilder()
					.setName("key")
					.setNumber(1)
					.setType(findKeyType(ctx.keyType()));
			FieldDescriptorProto.Builder value = FieldDescriptorProto.newBuilder()
					.setName("value")
					.setNumber(2)
					.setType(findType(ctx.type()));
			if (value.getType() == FieldDescriptorProto.Type.TYPE_MESSAGE
					|| value.getType() == FieldDescriptorProto.Type.TYPE_ENUM) {
				value.setTypeName(ctx.type().getText());
			}
			type.setOptions(type.getOptionsBuilder().setMapEntry(true));
			type.addField(key.build());
			type.addField(value.build());
			return type.build();
		}

		private FieldDescriptorProto.Type findKeyType(KeyTypeContext ctx) {
			if (ctx.STRING() != null) {
				return FieldDescriptorProto.Type.TYPE_STRING;
			}
			if (ctx.INT32() != null) {
				return FieldDescriptorProto.Type.TYPE_INT32;
			}
			if (ctx.INT64() != null) {
				return FieldDescriptorProto.Type.TYPE_INT64;
			}
			if (ctx.BOOL() != null) {
				return FieldDescriptorProto.Type.TYPE_BOOL;
			}
			if (ctx.FIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED32;
			}
			if (ctx.FIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED64;
			}
			if (ctx.SFIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED32;
			}
			if (ctx.SFIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED64;
			}
			if (ctx.UINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT32;
			}
			if (ctx.UINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT64;
			}
			if (ctx.SINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT32;
			}
			if (ctx.SINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT64;
			}
			throw new IllegalStateException("Unknown type: " + ctx.getText());
		}

		private FieldDescriptorProto.Type findType(TypeContext ctx) {
			if (ctx.STRING() != null) {
				return FieldDescriptorProto.Type.TYPE_STRING;
			}
			if (ctx.INT32() != null) {
				return FieldDescriptorProto.Type.TYPE_INT32;
			}
			if (ctx.INT64() != null) {
				return FieldDescriptorProto.Type.TYPE_INT64;
			}
			if (ctx.BOOL() != null) {
				return FieldDescriptorProto.Type.TYPE_BOOL;
			}
			if (ctx.FLOAT() != null) {
				return FieldDescriptorProto.Type.TYPE_FLOAT;
			}
			if (ctx.DOUBLE() != null) {
				return FieldDescriptorProto.Type.TYPE_DOUBLE;
			}
			if (ctx.BYTES() != null) {
				return FieldDescriptorProto.Type.TYPE_BYTES;
			}
			if (ctx.FIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED32;
			}
			if (ctx.FIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_FIXED64;
			}
			if (ctx.SFIXED32() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED32;
			}
			if (ctx.SFIXED64() != null) {
				return FieldDescriptorProto.Type.TYPE_SFIXED64;
			}
			if (ctx.UINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT32;
			}
			if (ctx.UINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_UINT64;
			}
			if (ctx.SINT32() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT32;
			}
			if (ctx.SINT64() != null) {
				return FieldDescriptorProto.Type.TYPE_SINT64;
			}
			if (ctx.messageType() != null) {
				if (this.localEnumNames.contains(ctx.messageType().getText())
						|| FileDescriptorProtoParser.this.enumNames.contains(ctx.messageType().getText())) {
					return FieldDescriptorProto.Type.TYPE_ENUM;
				}
				return FieldDescriptorProto.Type.TYPE_MESSAGE;
			}
			if (ctx.enumType() != null) {
				// Doesn't happen
				return FieldDescriptorProto.Type.TYPE_ENUM;
			}
			throw new IllegalStateException("Unknown type: " + ctx.getText());
		}

		@Override
		public FileDescriptorProto.Builder visitEnumDef(EnumDefContext ctx) {
			EnumDescriptorProto.Builder enumType = EnumDescriptorProto.newBuilder().setName(ctx.enumName().getText());
			this.enumType.push(enumType);
			FileDescriptorProto.Builder result = super.visitEnumDef(ctx);
			builder.addEnumType(enumType.build());
			this.enumType.pop();
			return result;
		}

		@Override
		public FileDescriptorProto.Builder visitEnumField(EnumFieldContext ctx) {
			// System.err.println("Enum field: " + ctx.enumFieldName().getText());
			EnumValueDescriptorProto.Builder field = EnumValueDescriptorProto.newBuilder()
					.setName(ctx.ident().IDENTIFIER().getText())
					.setNumber(Integer.valueOf(ctx.intLit().INT_LIT().getText()));
			this.enumType.peek().addValue(field.build());
			return super.visitEnumField(ctx);
		}

		@Override
		public FileDescriptorProto.Builder visitMessageDef(ProtobufParser.MessageDefContext ctx) {
			// System.err.println("Message: " + ctx.messageName().getText());
			DescriptorProto.Builder type = DescriptorProto.newBuilder().setName(ctx.messageName().getText());
			this.type.push(type);
			FileDescriptorProto.Builder result = super.visitMessageDef(ctx);
			builder.addMessageType(type);
			this.type.pop();
			return result;
		}

		@Override
		public FileDescriptorProto.Builder visitImportStatement(ImportStatementContext ctx) {
			String path = ctx.strLit().getText();
			path = path.replace("\"", "").replace("'", "");
			if (!cache.containsKey(path)) {
				parse(path, findImport(path));
			}
			builder.addDependency(path);
			return super.visitImportStatement(ctx);
		}

		@Override
		public Builder visitServiceDef(ServiceDefContext ctx) {
			String name = ctx.serviceName().getText();
			ServiceDescriptorProto.Builder service = ServiceDescriptorProto.newBuilder().setName(name);
			for (ProtobufParser.ServiceElementContext element : ctx.serviceElement()) {
				if (element.rpc() != null) {
					service.addMethod(buildRpc(element.rpc()));
				}
			}
			builder.addService(service.build());
			return super.visitServiceDef(ctx);
		}

		private MethodDescriptorProto buildRpc(RpcContext rpc) {
			String rpcName = rpc.rpcName().getText();
			MethodDescriptorProto.Builder method = MethodDescriptorProto.newBuilder()
					.setName(rpcName)
					.setInputType(rpc.messageType(0).messageName().getText())
					.setOutputType(rpc.messageType(1).messageName().getText());
			if (rpc.STREAM(0) != null) {
				method.setServerStreaming(true);
			}
			if (rpc.STREAM(1) != null) {
				method.setClientStreaming(true);
			}
			return method.build();
		}

	}

	static String capitalize(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}

		char baseChar = str.charAt(0);
		char updatedChar;
		updatedChar = Character.toUpperCase(baseChar);
		if (baseChar == updatedChar) {
			return str;
		}

		char[] chars = str.toCharArray();
		chars[0] = updatedChar;
		return new String(chars);
	}

}
