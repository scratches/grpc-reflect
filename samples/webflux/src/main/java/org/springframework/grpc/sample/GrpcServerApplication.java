/*
 * Copyright 2025-present the original author or authors.
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

package org.springframework.grpc.sample;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.protobuf.ProtoServiceDescriptorSupplier;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.grpc.reflection.v1.ErrorResponse;
import io.grpc.reflection.v1.FileDescriptorResponse;
import io.grpc.reflection.v1.ListServiceResponse;
import io.grpc.reflection.v1.ListServiceResponse.Builder;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionRequest.MessageRequestCase;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import reactor.core.publisher.Flux;

@SpringBootApplication
public class GrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

}

@RestController
class GrpcServerReflectionService {

	private Set<String> services = new HashSet<>();

	private Map<String, FileDescriptor> symbols = new HashMap<>();

	private Map<String, FileDescriptor> files = new HashMap<>();

	GrpcServerReflectionService() {
		this(SimpleGrpc.getServiceDescriptor(),
				ProtoReflectionServiceV1.newInstance().bindService().getServiceDescriptor());
	}

	public GrpcServerReflectionService(ServiceDescriptor... serviceDescriptors) {
		for (ServiceDescriptor serviceDescriptor : serviceDescriptors) {
			this.services.add(serviceDescriptor.getName());
			FileDescriptor fileDescriptor = ((ProtoServiceDescriptorSupplier) serviceDescriptor.getSchemaDescriptor())
				.getFileDescriptor();
			this.files.put(fileDescriptor.getName(), fileDescriptor);
			this.symbols.put(serviceDescriptor.getName(), fileDescriptor);
			for (MethodDescriptor<?, ?> method : serviceDescriptor.getMethods()) {
				// this.symbols.put(method.getFullMethodName(), fileDescriptor);
				this.symbols.put(method.getFullMethodName().replaceAll("/", "."), fileDescriptor);
			}
			for (Descriptor desc : fileDescriptor.getMessageTypes()) {
				this.symbols.put(desc.getFullName(), fileDescriptor);
			}
		}
		System.err.println("Registered services: " + this.symbols.keySet());
	}

	@PostMapping(path = "grpc.reflection.v1.ServerReflection/ServerReflectionInfo", produces = "application/grpc")
	public Flux<ServerReflectionResponse> postMethodName(@RequestBody Flux<ServerReflectionRequest> input) {
		return input.filter(request -> request.getMessageRequestCase() != MessageRequestCase.MESSAGEREQUEST_NOT_SET)
			.map(request -> response(request));
	}

	private ServerReflectionResponse response(ServerReflectionRequest request) {
		switch (request.getMessageRequestCase()) {
			case LIST_SERVICES:
				return listServicesResponse(request);
			case FILE_CONTAINING_SYMBOL:
				return fileContaining(request, request.getFileContainingSymbol(),
						this.symbols.get(request.getFileContainingSymbol()));
			case FILE_BY_FILENAME:
				return fileContaining(request, request.getFileByFilename(),
						this.files.get(request.getFileByFilename()));
			default:
				return errorResponse(request);
		}
	}

	private ServerReflectionResponse fileContaining(ServerReflectionRequest request, String symbol,
			FileDescriptor fileDescriptor) {
		if (fileDescriptor == null) {
			return ServerReflectionResponse.newBuilder()
				.setValidHost(request.getHost())
				.setOriginalRequest(request)
				.setErrorResponse(ErrorResponse.newBuilder()
					.setErrorCode(Status.Code.NOT_FOUND.value())
					.setErrorMessage("File not found: " + symbol))
				.build();
		}
		return ServerReflectionResponse.newBuilder()
			.setValidHost(request.getHost())
			.setOriginalRequest(request)
			.setFileDescriptorResponse(
					FileDescriptorResponse.newBuilder().addFileDescriptorProto(fileDescriptor.toProto().toByteString()))
			.build();
	}

	private ServerReflectionResponse errorResponse(ServerReflectionRequest request) {
		return ServerReflectionResponse.newBuilder()
			.setValidHost(request.getHost())
			.setOriginalRequest(request)
			.setErrorResponse(ErrorResponse.newBuilder()
				.setErrorCode(Status.Code.UNIMPLEMENTED.value())
				.setErrorMessage("not implemented " + request.getMessageRequestCase()))
			.build();
	}

	private ServerReflectionResponse listServicesResponse(ServerReflectionRequest request) {
		Builder services = ListServiceResponse.newBuilder();
		for (String serviceName : this.services) {
			services.addService(ServiceResponse.newBuilder().setName(serviceName).build());
		}
		return ServerReflectionResponse.newBuilder()
			.setValidHost(request.getHost())
			.setOriginalRequest(request)
			.setListServicesResponse(services)
			.build();
	}

}