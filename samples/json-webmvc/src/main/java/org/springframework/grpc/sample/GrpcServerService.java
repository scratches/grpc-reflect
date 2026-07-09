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

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.grpc.sample.proto.SimpleGrpc;
import org.springframework.grpc.util.MultiValueObserver;
import org.springframework.grpc.util.SingleValueObserver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;

@RestController
public class GrpcServerService extends SimpleGrpc.SimpleImplBase {

	private static Log log = LogFactory.getLog(GrpcServerService.class);

	@Override
	public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
		log.info("Hello " + req.getName());
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException();
		}
		HelloReply reply = HelloReply.newBuilder().setMessage("Hello ==> " + req.getName()).build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	@Override
	public void streamHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
		log.info("Hello " + req.getName());
		Flux.interval(Duration.ofSeconds(1))
				.take(10)
				.map(count -> HelloReply.newBuilder().setMessage("Hello(" + count + ") ==> " + req.getName()).build())
				.subscribe(responseObserver::onNext, responseObserver::onError, responseObserver::onCompleted);
	}

	@Override
	public StreamObserver<HelloRequest> parallelHello(StreamObserver<HelloReply> responseObserver) {
		return new StreamObserver<HelloRequest>() {

			@Override
			public void onNext(HelloRequest value) {
				log.info("Hello " + value.getName());
				HelloReply reply = HelloReply.newBuilder().setMessage("Hello ==> " + value.getName()).build();
				responseObserver.onNext(reply);
			}

			@Override
			public void onError(Throwable t) {
				responseObserver.onError(t);
			}

			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}

		};
	}

	@PostMapping(path = "Simple/SayHello", produces = "application/json")
	public HelloReply sayHello(@RequestBody HelloRequest req) {
		SingleValueObserver<HelloReply> observer = new SingleValueObserver<>();
		sayHello(req, observer);
		return observer.getValue();
	}

	@PostMapping(path = "Simple/StreamHello", produces = "application/jsonl+x-ndjson")
	public Flux<HelloReply> stream(@RequestBody HelloRequest req) {
		MultiValueObserver<HelloReply> observer = new MultiValueObserver<>();
		streamHello(req, observer);
		return observer.getValue();
	}

}