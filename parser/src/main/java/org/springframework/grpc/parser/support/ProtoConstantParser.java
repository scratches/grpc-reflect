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
package org.springframework.grpc.parser.support;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.springframework.grpc.parser.ConstantBaseListener;
import org.springframework.grpc.parser.ConstantBaseVisitor;
import org.springframework.grpc.parser.ConstantLexer;
import org.springframework.grpc.parser.ConstantParser;
import org.springframework.grpc.parser.ConstantParser.BlockLitContext;
import org.springframework.grpc.parser.ConstantParser.BoolLitContext;
import org.springframework.grpc.parser.ConstantParser.FloatLitContext;
import org.springframework.grpc.parser.ConstantParser.FullIdentContext;
import org.springframework.grpc.parser.ConstantParser.IdentContext;
import org.springframework.grpc.parser.ConstantParser.IntLitContext;
import org.springframework.grpc.parser.ConstantParser.StrLitContext;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DynamicMessage.Builder;

public class ProtoConstantParser {

	private FieldDescriptor field;

	public ProtoConstantParser(FieldDescriptor field) {
		this.field = field;
	}

	public Object parse(CharStream stream) {

		ConstantLexer lexer = new ConstantLexer(stream);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ConstantParser parser = new ConstantParser(tokens);

		parser.removeErrorListeners(); // Remove default error listeners
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				throw new IllegalStateException("Syntax error at line " + line + ": " + msg, e);
			}
		});
		parser.addParseListener(new ConstantBaseListener() {
		});

		Descriptor descriptor = descriptor();
		if (descriptor == null) {
			return parser.constant().accept(new LiteralVisitor());
		}
		Builder builder = DynamicMessage.newBuilder(field.getMessageType());
		DynamicMessage proto = parser.constant()
				.accept(new BlockVisitor(builder))
				.build();
		return proto;
	}

	private Descriptor descriptor() {
		if (field == null) {
			return null;
		}
		if (!field.getType().equals(FieldDescriptor.Type.MESSAGE)) {
			return null; // Not a message type, no descriptor needed
		}
		return field.getMessageType() == null ? null : field.getMessageType();
	}

	class LiteralVisitor extends ConstantBaseVisitor<Object> {

		private Object value = null;

		@Override
		protected Object defaultResult() {
			return value;
		}

		@Override
		public Object visitBoolLit(BoolLitContext ctx) {
			value = Boolean.parseBoolean(ctx.getText());
			return value;
		}

		@Override
		public Object visitIntLit(IntLitContext ctx) {
			value = Integer.parseInt(ctx.getText());
			return value;
		}

		@Override
		public Object visitFloatLit(FloatLitContext ctx) {
			value = Float.parseFloat(ctx.getText());
			return value;
		}

		@Override
		public Object visitStrLit(StrLitContext ctx) {
			String text = ctx.getText();
			String delimiter = text.substring(0, 1);
			value = text.replace(delimiter, "");
			return value;
		}

		@Override
		public Object visitFullIdent(FullIdentContext ctx) {
			// Probably this is an error, but we can return the text for now
			value = new Identifier(ctx.getText());
			return value;
		}
	}

	record Identifier(String name) {
	}

	class BlockVisitor extends ConstantBaseVisitor<Builder> {
		private Builder builder;

		public BlockVisitor(Builder builder) {
			this.builder = builder;
		}

		@Override
		protected Builder defaultResult() {
			return builder;
		}

		@Override
		public Builder visitBlockLit(BlockLitContext ctx) {
			int i = 0;
			for (IdentContext ident : ctx.ident()) {
				String name = ident.getText();
				FieldDescriptor field = builder.getDescriptorForType().findFieldByName(name);
				if (field == null) {
					throw new IllegalStateException("Unknown field: " + name);
				}
				if (field.getType() == FieldDescriptor.Type.MESSAGE) {
					Builder nestedBuilder = DynamicMessage.newBuilder(field.getMessageType());
					builder.setField(field, ctx.constant(i).accept(new BlockVisitor(nestedBuilder)).build());
				} else if (field.getType() == FieldDescriptor.Type.ENUM) {
					EnumValueDescriptor value = field.getEnumType().findValueByName(ctx.constant(i).getText());
					builder.setField(field, value);
				} else {
					Object value = ctx.constant(i).accept(new LiteralVisitor());
					builder.setField(field, value);
				}
				i++;
			}
			return builder;
		}
	}
}
