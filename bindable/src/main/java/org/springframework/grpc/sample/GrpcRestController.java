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

import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerMethodDefinition;
import io.grpc.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@RestController
public class GrpcRestController {

	@PostMapping(path = "Simple/SayHello", produces = "application/grpc")
	public HelloReply unary(@RequestBody HelloRequest input) {
		GrpcServerService service = new GrpcServerService();
		ServerMethodDefinition<HelloRequest, HelloReply> serverMethod = (ServerMethodDefinition<HelloRequest, HelloReply>) service
				.bindService().getMethod("Simple/SayHello");
		var method = serverMethod.getMethodDescriptor();
		var call = new OneToOneServerCall<HelloRequest, HelloReply>(method);
		var listener = serverMethod.getServerCallHandler().startCall(call, null);
		listener.onMessage(input);
		listener.onReady();
		listener.onHalfClose();
		listener.onComplete();
		HelloReply entity = call.response;
		return entity;
	}

	@PostMapping(path = "Simple/StreamHello", produces = "application/grpc")
	public Flux<HelloReply> stream(@RequestBody HelloRequest input) {
		GrpcServerService service = new GrpcServerService();
		ServerMethodDefinition<HelloRequest, HelloReply> serverMethod = (ServerMethodDefinition<HelloRequest, HelloReply>) service
				.bindService().getMethod("Simple/StreamHello");
		var method = serverMethod.getMethodDescriptor();
		return Flux.<HelloReply>create(emitter -> {
			var call = new OneToManyServerCall<HelloRequest, HelloReply>(method, emitter);
			var listener = serverMethod.getServerCallHandler().startCall(call, null);
			listener.onMessage(input);
			listener.onReady();
			listener.onHalfClose();
			listener.onComplete();
		}).subscribeOn(Schedulers.boundedElastic(), false);
	}

	abstract class LocalServerCall<Req, Res> extends ServerCall<Req, Res> {
		private MethodDescriptor<Req, Res> method;

		public LocalServerCall(MethodDescriptor<Req, Res> method) {
			this.method = method;
		}

		@Override
		public void request(int numMessages) {
			System.out.println("Requesting " + numMessages + " messages");
		}

		@Override
		public void close(Status status, Metadata trailers) {
			System.out.println("Call closed with status: " + status);
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public void sendHeaders(Metadata headers) {
			System.out.println("Sending headers: " + headers);
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
			System.out.println("Sending message: " + message);
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
			System.out.println("Sending streaming message: " + message);
			this.emitter.next(message);
		}

		@Override
		public void close(Status status, Metadata trailers) {
			System.out.println("Streaming call closed with status: " + status);
			emitter.complete();
		}
	}

}
