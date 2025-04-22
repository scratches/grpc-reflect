package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.test.annotation.DirtiesContext;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.MethodDescriptor.ReflectableMarshaller;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;

@SpringBootTest(properties = { "spring.grpc.server.port=0",
		"spring.grpc.client.default-channel.address=0.0.0.0:${local.grpc.port}" }, useMainMethod = UseMainMethod.ALWAYS)
@DirtiesContext
public class GrpcServerApplicationTests {

	private static Log log = LogFactory.getLog(GrpcServerApplicationTests.class);

	public static void main(String[] args) {
		new SpringApplicationBuilder(GrpcServerApplication.class, ExtraConfiguration.class).run();
	}

	@Autowired
	private SimpleGrpc.SimpleBlockingStub stub;

	@Test
	void contextLoads() {
	}

	@Test
	void serverResponds() {
		log.info("Testing");
		HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Alien").build());
		assertEquals("Hello ==> Alien", response.getMessage());
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExtraConfiguration {

		@Bean
		BindableService runner() {
			MethodDescriptor<Object, Object> method = MethodDescriptor.newBuilder()
					.setFullMethodName("Foo/Echo")
					.setRequestMarshaller(new FooMarshaller())
					.setResponseMarshaller(new FooMarshaller())
					.setSchemaDescriptor(new FooDescriptor())
					.setType(MethodType.UNARY)
					.build();
			ServerCallHandler<Object, Object> handler = new ServerCallHandler<Object, Object>() {
				@Override
				public Listener<Object> startCall(ServerCall<Object, Object> call, Metadata headers) {
					return new FooListener(call, headers);
				}
			};
			ServerServiceDefinition service = ServerServiceDefinition
					.builder(ServiceDescriptor.newBuilder("Foo")
							.setSchemaDescriptor(new FooDescriptor())
							.addMethod(method)
							.build())
					.addMethod(method, handler)
					.build();
			return () -> service;
		}

	}

	private static abstract class SimpleBaseDescriptorSupplier
			implements io.grpc.protobuf.ProtoServiceDescriptorSupplier {

		SimpleBaseDescriptorSupplier() {
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
			try {
				return com.google.protobuf.Descriptors.FileDescriptor
						.buildFrom(FileDescriptorProto.parseFrom(
								"""
								syntax = "proto3";
								service Foo {
									rpc Echo (EchoRequest) returns (EchoRequest) {
									}
								}
								message EchoRequest {
									string name = 1;
								}
										""".getBytes()), new FileDescriptor[0]);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
			return getFileDescriptor().findServiceByName("Foo");
		}

	}

	static class EchoDescriptor extends SimpleBaseDescriptorSupplier
			implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

		private final String methodName;

		EchoDescriptor(String methodName) {
			this.methodName = methodName;
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
			return getServiceDescriptor().findMethodByName(methodName);
		}

	}

	static class FooDescriptor implements ProtoServiceDescriptorSupplier {

		@java.lang.Override
		public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
			try {
				return com.google.protobuf.Descriptors.FileDescriptor
						.buildFrom(FileDescriptorProto.parseFrom(
								"""
								syntax = "proto3";
								service Foo {
									rpc Echo (EchoRequest) returns (EchoRequest) {
									}
								}
								message EchoRequest {
									string name = 1;
								}
										""".getBytes()), new FileDescriptor[0]);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@java.lang.Override
		public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
			return getFileDescriptor().findServiceByName("Foo");
		}

	}

	static class FooMarshaller implements ReflectableMarshaller<Object> {

		@Override
		public InputStream stream(Object value) {
			throw new UnsupportedOperationException("Unimplemented method 'stream'");
		}

		@Override
		public Object parse(InputStream stream) {
			throw new UnsupportedOperationException("Unimplemented method 'parse'");
		}

		@Override
		public Class<Object> getMessageClass() {
			return Object.class;
		}

	}

	static class FooListener extends ServerCall.Listener<Object> {

		public FooListener(ServerCall<Object, Object> call, Metadata headers) {
		}

		@Override
		public void onCancel() {
			super.onCancel();
		}

		@Override
		public void onComplete() {
			super.onComplete();
		}

		@Override
		public void onHalfClose() {
			super.onHalfClose();
		}

		@Override
		public void onMessage(Object message) {
			super.onMessage(message);
		}

		@Override
		public void onReady() {
			super.onReady();
		}

	}

}
