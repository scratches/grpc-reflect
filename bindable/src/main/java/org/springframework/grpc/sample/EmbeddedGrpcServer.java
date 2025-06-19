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
package org.springframework.grpc.sample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.grpc.Context;
import io.grpc.InternalServer;
import io.grpc.MethodDescriptor.PrototypeMarshaller;
import io.grpc.Server;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;

public class EmbeddedGrpcServer extends Server {

	static final Context.Key<Server> SERVER_CONTEXT_KEY = InternalServer.SERVER_CONTEXT_KEY;

	private volatile boolean shutdown;

	private List<ServerServiceDefinition> services = new ArrayList<>();

	private Map<String, HandlerFunction<ServerResponse>> handlers = new HashMap<>();

	private GrpcRequestHandler<Object, Object> requestHandler = new GrpcRequestHandler<>(this);

	@Override
	public Server start() throws IOException {
		Context.current().withValue(SERVER_CONTEXT_KEY, this);
		return this;
	}

	@Override
	public Server shutdown() {
		if (!this.shutdown) {
			this.shutdown = true;
		}
		return this;
	}

	@Override
	public Server shutdownNow() {
		return shutdown();
	}

	@Override
	public boolean isShutdown() {
		return this.shutdown;
	}

	@Override
	public boolean isTerminated() {
		return this.shutdown;
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		shutdown();
		return this.shutdown;
	}

	@Override
	public void awaitTermination() throws InterruptedException {
		shutdown();
	}

	@Override
	public List<ServerServiceDefinition> getServices() {
		return this.services;
	}

	@Override
	public List<ServerServiceDefinition> getMutableServices() {
		return this.getServices();
	}

	public void addService(ServerServiceDefinition service) {
		this.services.add(service);
		for (ServerMethodDefinition<?, ?> definition : service.getMethods()) {
			@SuppressWarnings("unchecked")
			ServerMethodDefinition<Object, Object> method = (ServerMethodDefinition<Object, Object>) definition;
			Class<?> inputType = ((PrototypeMarshaller<?>) method.getMethodDescriptor()
					.getRequestMarshaller()).getMessageClass();
			Class<?> outputType = ((PrototypeMarshaller<?>) method.getMethodDescriptor()
					.getResponseMarshaller()).getMessageClass();
			this.handlers.put("/" + method.getMethodDescriptor().getFullMethodName(),
					request -> ServerResponse.ok().contentType(MediaType.valueOf("application/grpc"))
							.body(requestHandler.handle(method, request.bodyToMono(inputType)),
									outputType));
		}
	}

	public Map<String, HandlerFunction<ServerResponse>> getHandlers() {
		return this.handlers;
	}
}
