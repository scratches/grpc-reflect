package org.springframework.grpc.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

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
import io.grpc.Status;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
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

	@Test
	void pojoCall() {
		DynamicStub stub = new DynamicStub(new DescriptorRegistry(), this.stub.getChannel());
		Hello request = new Hello();
		request.setName("Alien");
		Hello response = stub.call("Simple/SayHello", request, Hello.class);
		assertEquals("Hello ==> Alien", response.getName());
	}

	static class Hello {
		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExtraConfiguration {

		@Bean
		BindableService runner() {
			MethodDescriptor<DynamicMessage, DynamicMessage> method = MethodDescriptor
					.<DynamicMessage, DynamicMessage>newBuilder()
					.setFullMethodName("Foo/Echo")
					.setRequestMarshaller(new FooMarshaller())
					.setResponseMarshaller(new FooMarshaller())
					.setSchemaDescriptor(new SimpleBaseDescriptorSupplier())
					.setType(MethodType.UNARY)
					.build();
			ServerCallHandler<DynamicMessage, DynamicMessage> handler = new ServerCallHandler<DynamicMessage, DynamicMessage>() {
				@Override
				public Listener<DynamicMessage> startCall(ServerCall<DynamicMessage, DynamicMessage> call,
						Metadata headers) {
					call.request(2);
					return new FooListener(call, headers);
				}
			};
			ServerServiceDefinition service = ServerServiceDefinition
					.builder(ServiceDescriptor.newBuilder("Foo")
							.setSchemaDescriptor(new EchoDescriptor("Echo"))
							.addMethod(method)
							.build())
					.addMethod(method, handler)
					.build();
			return () -> service;
		}

	}

	private static class SimpleBaseDescriptorSupplier
			implements ProtoServiceDescriptorSupplier {

		private FileDescriptor descriptor;

		SimpleBaseDescriptorSupplier() {
			try {
				var foot = DescriptorProto.newBuilder()
						.setName("FooRequest")
						.addField(
								FieldDescriptorProto.newBuilder()
										.setName("name")
										.setNumber(1)
										.setType(FieldDescriptorProto.Type.TYPE_STRING))
						.addField(
								FieldDescriptorProto.newBuilder()
										.setName("age")
										.setNumber(2)
										.setType(FieldDescriptorProto.Type.TYPE_INT32))
						.build();
				var echo = DescriptorProtos.ServiceDescriptorProto.newBuilder()
						.setName("Foo")
						.addMethod(
								DescriptorProtos.MethodDescriptorProto.newBuilder()
										.setName("Echo")
										.setInputType("FooRequest")
										.setOutputType("FooRequest")
										.build())
						.build();
				var food = FileDescriptorProto.newBuilder()
						.setName("foo.proto")
						.setSyntax("proto3")
						.addMessageType(foot)
						.addService(echo).build();
				this.descriptor = FileDescriptor
						.buildFrom(food, new FileDescriptor[0]);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public FileDescriptor getFileDescriptor() {
			return this.descriptor;
		}

		@Override
		public Descriptors.ServiceDescriptor getServiceDescriptor() {
			return getFileDescriptor().findServiceByName("Foo");
		}

	}

	static class EchoDescriptor extends SimpleBaseDescriptorSupplier
			implements ProtoMethodDescriptorSupplier {

		private final String methodName;

		EchoDescriptor(String methodName) {
			this.methodName = methodName;
		}

		@java.lang.Override
		public Descriptors.MethodDescriptor getMethodDescriptor() {
			return getServiceDescriptor().findMethodByName(methodName);
		}

	}

	static class FooMarshaller implements ReflectableMarshaller<DynamicMessage> {

		private Descriptor type;

		FooMarshaller() {
			this.type = new SimpleBaseDescriptorSupplier().getFileDescriptor().getMessageTypes().get(0);
		}

		@Override
		public InputStream stream(DynamicMessage value) {
			try {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				value.writeTo(output);
				return new ByteArrayInputStream(output.toByteArray());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public DynamicMessage parse(InputStream stream) {
			DynamicMessage.Builder builder = DynamicMessage.newBuilder(type);
			try {
				return builder.mergeFrom(stream).build();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public Class<DynamicMessage> getMessageClass() {
			return DynamicMessage.class;
		}

	}

	static class FooListener extends ServerCall.Listener<DynamicMessage> {

		private ServerCall<DynamicMessage, DynamicMessage> call;
		private Metadata headers;

		public FooListener(ServerCall<DynamicMessage, DynamicMessage> call, Metadata headers) {
			this.call = call;
			this.headers = headers;
		}

		@Override
		public void onCancel() {
		}

		@Override
		public void onComplete() {
		}

		@Override
		public void onHalfClose() {
		}

		@Override
		public void onMessage(DynamicMessage message) {
			this.call.sendMessage(message);
			this.call.close(Status.OK, new Metadata());
		}

		@Override
		public void onReady() {
			call.sendHeaders(this.headers);
		}

	}

}
