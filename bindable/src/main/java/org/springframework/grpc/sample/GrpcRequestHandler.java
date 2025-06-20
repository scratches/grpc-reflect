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

import org.reactivestreams.Publisher;
import org.springframework.web.reactive.function.server.ServerRequest;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import io.grpc.MethodDescriptor.PrototypeMarshaller;
import io.grpc.ServerCall;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import io.grpc.reflection.v1.ServerReflectionResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.core.scheduler.Schedulers;

public class GrpcRequestHandler<I, O> {

	private EmbeddedGrpcServer server;

	public GrpcRequestHandler(EmbeddedGrpcServer server) {
		this.server = server;
	}

	public Class<I> getInputType(ServerMethodDefinition<I, O> serverMethod) {
		return ((PrototypeMarshaller<I>) serverMethod.getMethodDescriptor().getRequestMarshaller())
				.getMessageClass();
	}

	public Class<O> getOutputType(ServerMethodDefinition<I, O> serverMethod) {
		return ((PrototypeMarshaller<O>) serverMethod.getMethodDescriptor().getResponseMarshaller())
				.getMessageClass();
	}

	public Publisher<O> handle(ServerMethodDefinition<I, O> serverMethod, ServerRequest request) {
		Class<?> inputType = getInputType(serverMethod);
		@SuppressWarnings("unchecked")
		Publisher<I> input = (Publisher<I>) (serverMethod.getMethodDescriptor().getType() == MethodType.BIDI_STREAMING
				? request.bodyToFlux(inputType)
				: request.bodyToMono(inputType));
		switch (serverMethod.getMethodDescriptor().getType()) {
			case UNARY:
				return Mono.from(input).map(item -> unary(item, serverMethod));
			case SERVER_STREAMING:
				return Mono.from(input).flatMapMany(item -> stream(item, serverMethod));
			case BIDI_STREAMING:
				return bidi(Flux.from(input), serverMethod);
			default:
				throw new UnsupportedOperationException(
						"Unsupported method type: " + serverMethod.getMethodDescriptor().getType());
		}
	}

	private O unary(I input, ServerMethodDefinition<I, O> serverMethod) {
		var method = serverMethod.getMethodDescriptor();
		var call = new OneToOneServerCall<I, O>(method);
		var listener = serverMethod.getServerCallHandler().startCall(call, null);
		listener.onMessage(input);
		listener.onReady();
		listener.onHalfClose();
		listener.onComplete();
		O entity = call.response;
		return entity;
	}

	private Flux<O> stream(I input, ServerMethodDefinition<I, O> serverMethod) {
		var method = serverMethod.getMethodDescriptor();
		return Flux.<O>create(emitter -> {
			var call = new OneToManyServerCall<I, O>(method, emitter);
			var listener = serverMethod.getServerCallHandler().startCall(call, null);
			listener.onMessage(input);
			listener.onReady();
			listener.onHalfClose();
			listener.onComplete();
		}).subscribeOn(Schedulers.boundedElastic(), false);
	}

	private Flux<O> bidi(Flux<I> request, ServerMethodDefinition<I, O> serverMethod) {
		var method = serverMethod.getMethodDescriptor();
		Context context = Context.current().withValue(EmbeddedGrpcServer.SERVER_CONTEXT_KEY, this.server);
		Context previous = context.attach();
		try {
			var call = new ManyToManyServerCall<I, O>(method);
			var listener = serverMethod.getServerCallHandler().startCall(call, null);
			return request.flatMap(input -> {
						listener.onMessage(input);
						listener.onReady();
						return call.reset();
					})
					.subscribeOn(Schedulers.boundedElastic(), false)
					.doFinally(signalType -> {
						listener.onHalfClose();
						listener.onComplete();
					});
		} finally {
			context.detach(previous);
		}
	}

	abstract class LocalServerCall<Req, Res> extends ServerCall<Req, Res> {
		private MethodDescriptor<Req, Res> method;

		public LocalServerCall(MethodDescriptor<Req, Res> method) {
			this.method = method;
		}

		@Override
		public void request(int numMessages) {
		}

		@Override
		public void close(Status status, Metadata trailers) {
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public void sendHeaders(Metadata headers) {
		}

		@Override
		public MethodDescriptor<Req, Res> getMethodDescriptor() {
			return method;
		}

	}

	class OneToOneServerCall<Req, Res> extends LocalServerCall<Req, Res> {

		private Res response;

		public OneToOneServerCall(MethodDescriptor<Req, Res> method) {
			super(method);
		}

		@Override
		public void sendMessage(Res message) {
			this.response = message;
		}
	}

	class OneToManyServerCall<Req, Res> extends LocalServerCall<Req, Res> {

		private FluxSink<Res> emitter;

		public OneToManyServerCall(MethodDescriptor<Req, Res> method, FluxSink<Res> emitter) {
			super(method);
			this.emitter = emitter;
		}

		@Override
		public void sendMessage(Res message) {
			this.emitter.next(message);
		}

		@Override
		public void close(Status status, Metadata trailers) {
			emitter.complete();
		}
	}

	class ManyToManyServerCall<Req, Res> extends LocalServerCall<Req, Res> {

		private Many<Res> emitter = Sinks.many().unicast().onBackpressureBuffer();

		public ManyToManyServerCall(MethodDescriptor<Req, Res> method) {
			super(method);
		}

		@Override
		public void sendMessage(Res message) {
			if (message instanceof ServerReflectionResponse response) {
				if (response.getErrorResponse() != null && response.getErrorResponse().getErrorCode() != 0) {
					return;
				}
			}
			this.emitter.tryEmitNext(message);
		}

		public Flux<Res> reset() {
			Many<Res> emitter = this.emitter;
			emitter.tryEmitComplete();
			this.emitter = Sinks.many().unicast().onBackpressureBuffer();
			// Reset the emitter for the next call
			return emitter.asFlux();
		}
	}
}
