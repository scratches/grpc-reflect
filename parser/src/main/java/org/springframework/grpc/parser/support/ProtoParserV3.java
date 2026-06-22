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

package org.springframework.grpc.parser.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Consumer;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.springframework.grpc.parser.v3.ProtobufBaseListener;
import org.springframework.grpc.parser.v3.ProtobufBaseVisitor;
import org.springframework.grpc.parser.v3.ProtobufLexer;
import org.springframework.grpc.parser.v3.ProtobufParser;
import org.springframework.grpc.parser.v3.ProtobufParser.BlockLitContext;
import org.springframework.grpc.parser.v3.ProtobufParser.ConstantContext;
import org.springframework.grpc.parser.v3.ProtobufParser.EnumDefContext;
import org.springframework.grpc.parser.v3.ProtobufParser.EnumFieldContext;
import org.springframework.grpc.parser.v3.ProtobufParser.ExtendDefContext;
import org.springframework.grpc.parser.v3.ProtobufParser.FieldContext;
import org.springframework.grpc.parser.v3.ProtobufParser.FieldLabelContext;
import org.springframework.grpc.parser.v3.ProtobufParser.ImportStatementContext;
import org.springframework.grpc.parser.v3.ProtobufParser.KeyTypeContext;
import org.springframework.grpc.parser.v3.ProtobufParser.MapFieldContext;
import org.springframework.grpc.parser.v3.ProtobufParser.MessageDefContext;
import org.springframework.grpc.parser.v3.ProtobufParser.OptionStatementContext;
import org.springframework.grpc.parser.v3.ProtobufParser.PackageStatementContext;
import org.springframework.grpc.parser.v3.ProtobufParser.RpcContext;
import org.springframework.grpc.parser.v3.ProtobufParser.ServiceDefContext;
import org.springframework.grpc.parser.v3.ProtobufParser.TypeContext;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto.Builder;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

public class ProtoParserV3 {

	// TODO: extract shared code with V2

	private Set<String> enumNames = new HashSet<>();

	public FileDescriptorProto parse(String name, CharStream stream, Consumer<String> importHandler) {

		ProtobufLexer lexer = new ProtobufLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ProtobufParser parser = new ProtobufParser(tokens);

		Builder builder = FileDescriptorProto.newBuilder();
		builder.setName(name);
		builder.setSyntax("proto3");

		parser.removeErrorListeners(); // Remove default error listeners
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("Syntax error at line " + line + " in " + name + ": " + msg, e);
			}
		});
		Set<String> localEnumNames = new HashSet<>();
		parser.addParseListener(new ProtobufBaseListener() {
			private String packageName;

			private Deque<MessageDefContext> type = new ArrayDeque<>();

			@Override
			public void enterMessageDef(MessageDefContext ctx) {
				this.type.push(ctx);
			}

			@Override
			public void exitMessageDef(MessageDefContext ctx) {
				if (!this.type.isEmpty()) {
					this.type.pop();
				}
			}

			@Override
			public void exitPackageStatement(PackageStatementContext ctx) {
				String packageName = ctx.fullIdent().getText();
				if (!packageName.isEmpty()) {
					this.packageName = packageName;
				}
			}

			@Override
			public void exitEnumDef(EnumDefContext ctx) {
				String name = ctx.enumName().getText();
				localEnumNames.add(name);
				if (!this.type.isEmpty()) {
					for (MessageDefContext parent : this.type) {
						if (parent.messageName() != null) {
							name = parent.messageName().getText() + "." + name;
							localEnumNames.add(name);
						}
					}
				}
				if (this.packageName != null) {
					enumNames.add(this.packageName + "." + name);
				} else {
					enumNames.add(name);
				}
			}
		});

		parser.proto().accept(new ProtobufBaseVisitor<>() {
			@Override
			public Object visitImportStatement(ImportStatementContext ctx) {
				String path = ctx.strLit().getText();
				path = path.replace("\"", "").replace("'", "");
				importHandler.accept(path);
				return super.visitImportStatement(ctx);
			}
		});
		parser.reset();
		FileDescriptorProto proto = parser.proto()
				.accept(new ProtobufDescriptorVisitor(builder, localEnumNames))
				.build();
		return proto;
	}

	class ProtobufDescriptorVisitor extends ProtobufBaseVisitor<Builder> {

		private final Builder builder;

		private Stack<DescriptorProto.Builder> type = new Stack<>();

		private Stack<EnumDescriptorProto.Builder> enumType = new Stack<>();

		private Stack<FieldDescriptorProto.Builder> field = new Stack<>();

		private final Set<String> localEnumNames;

		public ProtobufDescriptorVisitor(Builder builder, Set<String> localEnumNames) {
			this.builder = builder;
			this.localEnumNames = localEnumNames;
		}

		@Override
		protected Builder defaultResult() {
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
		public Builder visitFieldLabel(FieldLabelContext ctx) {
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
		public Builder visitExtendDef(ExtendDefContext ctx) {
			DescriptorProto.Builder type = DescriptorProto.newBuilder().setName(ctx.messageType().getText());
			this.type.push(type);
			Builder result = super.visitExtendDef(ctx);
			FieldDescriptorProto field = type.getField(0);
			builder.addExtension(field.toBuilder().setExtendee(ctx.messageType().getText()).build());
			this.type.pop();
			return result;
		}

		@Override
		public Builder visitField(FieldContext ctx) {
			// TODO: handle field options if needed
			FieldDescriptorProto.Type fieldType = findType(ctx.type());
			FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
					.setName(ctx.fieldName().getText())
					.setNumber(parseInt(ctx.fieldNumber().getText()))
					.setType(fieldType);
			this.field.push(field);
			if (fieldType == FieldDescriptorProto.Type.TYPE_MESSAGE
					|| fieldType == FieldDescriptorProto.Type.TYPE_ENUM) {
				field.setTypeName(ctx.type().messageType().getText());
			}
			Builder result = super.visitField(ctx);
			this.type.peek().addField(field.build());
			this.field.pop();
			return result;
		}

		private int parseInt(String text) {
			if (text.startsWith("0x") || text.startsWith("0X")) {
				return Integer.parseInt(text.substring(2), 16);
			}
			if (text.startsWith("0") && text.length() > 1) {
				return Integer.parseInt(text.substring(1), 8);
			}
			return Integer.parseInt(text, 10);
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
			Builder result = super.visitMapField(ctx);
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
						|| ProtoParserV3.this.enumNames.contains(ctx.messageType().getText())) {
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
		public Builder visitEnumDef(EnumDefContext ctx) {
			EnumDescriptorProto.Builder enumType = EnumDescriptorProto.newBuilder().setName(ctx.enumName().getText());
			DescriptorProto.Builder owner = this.type.isEmpty() ? null : this.type.peek();
			this.enumType.push(enumType);
			Builder result = super.visitEnumDef(ctx);
			if (owner != null) {
				owner.addEnumType(enumType);
			} else {
				builder.addEnumType(enumType.build());
			}
			this.enumType.pop();
			return result;
		}

		@Override
		public Builder visitEnumField(EnumFieldContext ctx) {
			// System.err.println("Enum field: " + ctx.enumFieldName().getText());
			String name = ctx.ident().IDENTIFIER() == null ? ctx.ident().keywords().getText()
					: ctx.ident().IDENTIFIER().getText();
			EnumValueDescriptorProto.Builder field = EnumValueDescriptorProto.newBuilder()
					.setName(name)
					.setNumber(Integer.valueOf(ctx.intLit().INT_LIT().getText()));
			this.enumType.peek().addValue(field.build());
			return super.visitEnumField(ctx);
		}

		@Override
		public Builder visitMessageDef(ProtobufParser.MessageDefContext ctx) {
			// System.err.println("Message: " + ctx.messageName().getText());
			String name = ctx.messageName().getText();
			DescriptorProto.Builder type = DescriptorProto.newBuilder().setName(name);
			DescriptorProto.Builder owner = this.type.isEmpty() ? null : this.type.peek();
			this.type.push(type);
			Builder result = super.visitMessageDef(ctx);
			if (owner != null) {
				owner.addNestedType(type);
			} else {
				builder.addMessageType(type);
			}
			this.type.pop();
			return result;
		}

		@Override
		public Builder visitImportStatement(ImportStatementContext ctx) {
			String path = ctx.strLit().getText();
			path = path.replace("\"", "").replace("'", "");
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
			if (rpc.optionStatement() != null && rpc.optionStatement().size() > 0) {
				MethodOptions.Builder options = MethodOptions.newBuilder();
				for (OptionStatementContext option : rpc.optionStatement()) {
					Object value = extractConstant(option.constant(), MethodOptions.getDescriptor());
					String name = option.optionName().getText();
					name = name.replace("(", "").replace(")", "");
					FieldDescriptor field = MethodOptions.getDescriptor().findFieldByName(name);
					if (field == null) {
						// It's an extension
					} else {
						options.setField(field, value);
					}
				}
			}
			return method.build();
		}

		private Object extractConstant(ConstantContext constant, Descriptor descriptor) {
			if (constant.intLit() != null) {
				return Integer.valueOf(constant.intLit().INT_LIT().getText());
			}
			if (constant.strLit() != null) {
				return constant.strLit().getText().replace("\"", "").replace("'", "");
			}
			if (constant.boolLit() != null) {
				return Boolean.valueOf(constant.boolLit().BOOL_LIT().getText());
			}
			if (constant.blockLit() != null) {
				return extractBlockLit(constant.blockLit());
			}
			throw new IllegalStateException("Unknown constant: " + constant.getText());
		}

		private AbstractMessage extractBlockLit(BlockLitContext blockLit) {
			return null;
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
