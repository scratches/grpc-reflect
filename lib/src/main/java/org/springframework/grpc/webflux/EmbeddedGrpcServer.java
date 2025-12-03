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
package org.springframework.grpc.webflux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.InternalServer;
import io.grpc.Server;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;

/**
 * An embedded gRPC server implementation for Spring WebFlux applications.
 * <p>
 * This server extends the base {@link Server} class and provides integration with Spring
 * WebFlux, enabling gRPC services to run within a reactive Spring Boot application
 * context.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class EmbeddedGrpcServer extends Server {

	static final Context.Key<Server> SERVER_CONTEXT_KEY = InternalServer.SERVER_CONTEXT_KEY;

	private volatile boolean shutdown;

	private List<ServerServiceDefinition> services = new ArrayList<>();

	private Map<String, HandlerFunction<ServerResponse>> handlers = new HashMap<>();

	private GrpcRequestHandler<Object, Object> requestHandler = new GrpcRequestHandler<>(this);

	@Override
	public Server start() throws IOException {
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

	public void addService(BindableService bindable) {
		ServerServiceDefinition service = bindable.bindService();
		this.services.add(service);
		for (ServerMethodDefinition<?, ?> definition : service.getMethods()) {
			@SuppressWarnings("unchecked")
			ServerMethodDefinition<Object, Object> method = (ServerMethodDefinition<Object, Object>) definition;
			Class<?> outputType = requestHandler.getOutputType(method);
			this.handlers.put("/" + method.getMethodDescriptor().getFullMethodName(),
					request -> ServerResponse.ok()
						.contentType(MediaType.valueOf("application/grpc"))
						.body(requestHandler.handle(bindable, method, request), outputType));
		}
	}

	public Map<String, HandlerFunction<ServerResponse>> getHandlers() {
		return this.handlers;
	}

}
