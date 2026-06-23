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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.parser.ConstantBaseVisitor;
import org.springframework.grpc.parser.ConstantLexer;
import org.springframework.grpc.parser.ConstantParser;
import org.springframework.grpc.parser.ConstantParser.ConstantContext;
import org.springframework.grpc.parser.v3.ProtobufBaseVisitor;
import org.springframework.grpc.parser.v3.ProtobufLexer;
import org.springframework.grpc.parser.v3.ProtobufParser;
import org.springframework.grpc.parser.v3.ProtobufParser.ProtoContext;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

public class ProtoConstantParserTests {

	@Test
	public void testConstantParsing() {
		String input = """
				{
					name: "foo"
					flag: YES
					value: 42
				}
				""";
		AtomicInteger errorCount = new AtomicInteger(0);
		ConstantLexer lexer = new ConstantLexer(CharStreams.fromString(input));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ConstantParser parser = new ConstantParser(tokens);
		parser.addErrorListener(new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
					int charPositionInLine, String msg, RecognitionException e) {
				errorCount.incrementAndGet();
			}
		});
		ConstantContext context = parser.constant();
		context.accept(new ConstantBaseVisitor<>() {
		});
		assertThat(errorCount.get()).isEqualTo(0);
	}

	@Test
	public void testConstantParsingFromRuleContext() throws Exception {
		String input = """
				syntax = "proto3";
				message Foo {
					string name = 1;
				}
				service FooService {
					rpc Echo (EchoRequest) returns (EchoRequest) {
						option (foo) = { name: "test" };
					}
				}
				message EchoRequest {
					string name = 1;
				}
				message Bar {
					Foo foo = 1;
				}
				""";
		FileDescriptorProto proto = new ProtoParserV3().parse("test.proto", CharStreams.fromString(input));
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		FieldDescriptor field = file.getMessageType(2).getField(0);
		assertThat(field.getName()).isEqualTo("foo");
		assertThat(field.getMessageType().getName()).isEqualTo("Foo");
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
		DynamicMessage msg = (DynamicMessage) context.accept(new ProtobufBaseVisitor<>() {
			private Object value = null;

			@Override
			protected Object defaultResult() {
				return value;
			}

			@Override
			public Object visitConstant(org.springframework.grpc.parser.v3.ProtobufParser.ConstantContext ctx) {
				// There is only one constant in the input, so we can just parse it and return it
				ProtoConstantParser parser = new ProtoConstantParser(field);
				DynamicMessage msg = (DynamicMessage) parser.parse(ctx);
				this.value = msg;
				return msg;
			}
		});
		assertThat(errorCount.get()).isEqualTo(0);
		assertThat(msg).isNotNull();
		assertThat(msg.getAllFields()).hasSize(1);
	}

	@Test
	public void testSimpleMessageLiteral() throws Exception {
		String input = """
				{
					name: "bar"
				}
				""";
		FileDescriptorProto proto = new ProtoParserV3().parse("test.proto", CharStreams.fromString("""
				syntax = "proto3";
				message Foo {
					string name = 1;
				}
				message Bar {
					Foo foo = 1;
				}
				"""));
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		ProtoConstantParser parser = new ProtoConstantParser(file.getMessageType(1).getField(0));
		DynamicMessage msg = (DynamicMessage) parser.parse(CharStreams.fromString(input));
		assertThat(msg).isNotNull();
		assertThat(msg.getAllFields()).hasSize(1);
		FieldDescriptor next = file.getMessageType(0).getField(0);
		assertThat(next.getName()).isEqualTo("name");
		assertThat(msg.getField(next)).isEqualTo("bar");
	}

	@Test
	public void testMessageLiteral() throws Exception {
		String input = """
				{
					name: "bar"
					value: 23
				}
				""";
		FileDescriptorProto proto = new ProtoParserV3().parse("test.proto", CharStreams.fromString("""
				syntax = "proto3";
				message Foo {
					string name = 1;
					int32 value = 2;
				}
				message Bar {
					Foo foo = 1;
				}
				"""));
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		ProtoConstantParser parser = new ProtoConstantParser(file.getMessageType(1).getField(0));
		DynamicMessage msg = (DynamicMessage) parser.parse(CharStreams.fromString(input));
		assertThat(msg).isNotNull();
		assertThat(msg.getAllFields()).hasSize(2);
		FieldDescriptor next = file.getMessageType(0).getField(0);
		assertThat(next.getName()).isEqualTo("name");
		assertThat(msg.getField(next)).isEqualTo("bar");
		next = file.getMessageType(0).getField(1);
		assertThat(next.getName()).isEqualTo("value");
		assertThat(msg.getField(next)).isEqualTo(23);
	}

	@Test
	public void testNestedMessageLiteral() throws Exception {
		String input = """
				{
					foo: { name: "bar" }
				}
				""";
		FileDescriptorProto proto = new ProtoParserV3().parse("test.proto", CharStreams.fromString("""
				syntax = "proto3";
				message Foo {
					string name = 1;
				}
				message Bar {
					Foo foo = 1;
				}
				message Spam {
					int32 id = 1;
					Bar bar = 2;
				}
				"""));
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		ProtoConstantParser parser = new ProtoConstantParser(file.getMessageType(2).getField(1));
		DynamicMessage msg = (DynamicMessage) parser.parse(CharStreams.fromString(input));
		assertThat(msg).isNotNull();
		assertThat(msg.getAllFields()).hasSize(1);
		FieldDescriptor field = file.getMessageType(1).getField(0);
		assertThat(field.getName()).isEqualTo("foo");
		assertThat(msg.getField(field)).isNotNull();
		msg = (DynamicMessage) msg.getField(field);
		field = file.getMessageType(0).getField(0);
		assertThat(field.getName()).isEqualTo("name");
		assertThat(msg.getField(field)).isEqualTo("bar");
	}

	@Test
	public void testIntegerLiteral() throws Exception {
		String input = """
				1234
				""";
		ProtoConstantParser parser = new ProtoConstantParser(field(Type.TYPE_INT32));
		Integer value = (Integer) parser.parse(CharStreams.fromString(input));
		assertThat(value).isNotNull();
		assertThat(value).isEqualTo(1234);
	}

	@Test
	public void testFloatLiteral() throws Exception {
		String input = """
				1234.56
				""";
		ProtoConstantParser parser = new ProtoConstantParser(field(Type.TYPE_FLOAT));
		Float value = (Float) parser.parse(CharStreams.fromString(input));
		assertThat(value).isNotNull();
		assertThat(value).isEqualTo(1234.56f);
	}

	@Test
	public void testBooleanLiteral() throws Exception {
		String input = """
				true
				""";
		ProtoConstantParser parser = new ProtoConstantParser(field(Type.TYPE_BOOL));
		Boolean value = (Boolean) parser.parse(CharStreams.fromString(input));
		assertThat(value).isNotNull();
		assertThat(value).isEqualTo(true);
	}

	@Test
	public void testStringLiteral() throws Exception {
		String input = """
				"foo"
				""";
		ProtoConstantParser parser = new ProtoConstantParser(field(Type.TYPE_STRING));
		String value = (String) parser.parse(CharStreams.fromString(input));
		assertThat(value).isNotNull();
		assertThat(value).isEqualTo("foo");
	}

	@Test
	public void testLongStringLiteral() throws Exception {
		String input = """
				"foo"
				"bar"
				""";
		ProtoConstantParser parser = new ProtoConstantParser(field(Type.TYPE_STRING));
		String value = (String) parser.parse(CharStreams.fromString(input));
		assertThat(value).isNotNull();
		assertThat(value).isEqualTo("foobar");
	}

	@Test
	public void testEnumLiteral() throws Exception {
		String input = """
				{
					foo: SPAM
				}
				""";
		FileDescriptorProto proto = new ProtoParserV3().parse("test.proto", CharStreams.fromString("""
				syntax = "proto3";
				enum Foo {
					BAR = 0;
					SPAM = 1;
				}
				message Bar {
					Foo foo = 1;
				}
				message Spam {
					int32 id = 1;
					Bar bar = 2;
				}
				"""));
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		ProtoConstantParser parser = new ProtoConstantParser(file.getMessageType(1).getField(1));
		DynamicMessage msg = (DynamicMessage) parser.parse(CharStreams.fromString(input));
		assertThat(msg).isNotNull();
		assertThat(msg.getAllFields()).hasSize(1);
		FieldDescriptor next = file.getMessageType(0).getField(0);
		assertThat(next.getName()).isEqualTo("foo");
		Object value = msg.getField(next);
		assertThat(value).isInstanceOf(EnumValueDescriptor.class);
		assertThat(value.toString()).isEqualTo("SPAM");
	}

	private FieldDescriptor field(Type type) throws Exception {
		FieldDescriptorProto field = FieldDescriptorProto.newBuilder()
				.setName("foo").setType(type).setExtendee("Bar").setNumber(10)
				.build();
		FileDescriptorProto proto = FileDescriptorProto.newBuilder().setName("test.proto")
				.addExtension(field)
				.addMessageType(DescriptorProto.newBuilder().setName("Bar")
						.addField(FieldDescriptorProto.newBuilder()
								.setName("value")
								.setNumber(1)
								.setType(Type.TYPE_INT32)
								.build())
						.addExtensionRange(ExtensionRange.newBuilder().setStart(10).setEnd(20).build())
						.build())
				.build();
		FileDescriptor file = FileDescriptor.buildFrom(proto, new FileDescriptor[] {});
		return file.getExtension(0);
	}

}
