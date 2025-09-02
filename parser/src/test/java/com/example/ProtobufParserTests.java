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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Test;

import com.example.ProtobufParser.MessageBodyContext;
import com.example.ProtobufParser.ProtoContext;

public class ProtobufParserTests {

	@Test
	public void testMessageParsing() {
		String input = """
				syntax = "proto3";
				option java_multiple_files = true;
				option java_package = "com.example.proto";
				option java_outer_classname = "FooProto";
				service Foo {
					rpc Echo (EchoRequest) returns (EchoRequest) {}
				}
				message EchoRequest {
					string name = 1;
				}
				""";
		AtomicInteger errorCount = new AtomicInteger(0);
		ProtobufParser parser = new ProtobufParser(
				new CommonTokenStream(new ProtobufLexer(CharStreams.fromString(input))));
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				errorCount.incrementAndGet();
			}
		});
		ProtoContext context = parser.proto();
		context.accept(new ProtobufBaseVisitor<>() {
			@Override
			public Object visitMessageDef(ProtobufParser.MessageDefContext ctx) {
				System.err.println("Message: " + ctx.messageName().getText());
				return super.visitMessageDef(ctx);
			}

			@Override
			public Object visitMessageBody(MessageBodyContext ctx) {
				System.err.println("Field: " + ctx.messageElement().get(0).field().fieldName().getText());
				return super.visitMessageBody(ctx);
			}
		});
		assertThat(errorCount.get()).isEqualTo(0);
	}

	@Test
	public void testMessageParsingFailure() {
		String input = """
				syntax = "proto3";
				message EchoRequest {
					optional foo string name = 1;
				}
				""";
		ProtobufParser parser = new ProtobufParser(
				new CommonTokenStream(new ProtobufLexer(CharStreams.fromString(input))));
		AtomicInteger errorCount = new AtomicInteger(0);
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				errorCount.incrementAndGet();
			}
		});
		ProtoContext context = parser.proto();
		assertThat(context).isNotNull();
		assertThat(errorCount.get()).isEqualTo(1);
	}

	@Test
	public void testWrongProtoFailure() {
		String input = """
				syntax = "proto2";
				message EchoRequest {
					optional string name = 1;
				}
				""";
		ProtobufParser parser = new ProtobufParser(
				new CommonTokenStream(new ProtobufLexer(CharStreams.fromString(input))));
		AtomicInteger errorCount = new AtomicInteger(0);
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				errorCount.incrementAndGet();
			}
		});
		ProtoContext context = parser.proto();
		assertThat(context).isNotNull();
		assertThat(errorCount.get()).isEqualTo(1);
	}

}
