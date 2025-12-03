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
import org.springframework.grpc.sample.proto.ReactorHelloGrpc;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GrpcReactorService extends ReactorHelloGrpc.HelloImplBase {

	private static Log log = LogFactory.getLog(GrpcReactorService.class);

	@Override
	public Mono<HelloReply> sayHello(HelloRequest req) {
		log.info("Hello " + req.getName());
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException("Internal");
		}
		HelloReply reply = HelloReply.newBuilder().setMessage("Hello ==> " + req.getName()).build();
		return Mono.just(reply);
	}

	@Override
	public Flux<HelloReply> streamHello(HelloRequest req) {
		log.info("Hello " + req.getName());
		return Flux.interval(Duration.ofSeconds(1))
			.take(5)
			.map(i -> HelloReply.newBuilder().setMessage("Hello(" + i + ") ==> " + req.getName()).build());
	}

	@Override
	public Flux<HelloReply> parallelHello(Flux<HelloRequest> request) {
		return request.map(value -> {
			log.info("Hello " + value.getName());
			HelloReply reply = HelloReply.newBuilder().setMessage("Hello ==> " + value.getName()).build();
			return reply;
		});
	}

}