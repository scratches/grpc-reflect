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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.reflect.EnableGrpcMapping;
import org.springframework.grpc.reflect.GrpcController;
import org.springframework.grpc.reflect.GrpcMapping;

import reactor.core.publisher.Flux;

@SpringBootApplication
@EnableGrpcMapping
public class GrpcServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrpcServerApplication.class, args);
	}

}

@GrpcController
class FooService {

	@GrpcMapping("Echo")
	public Foo ping(Foo input) {
		return input;
	}

	@GrpcMapping
	public Output process(Input input) {
		return new Output();
	}

	@GrpcMapping
	public Flux<Output> stream(Input input) {
		return Flux.just(new Output());
	}

	static class Input {

	}

	static class Output {

	}

}
