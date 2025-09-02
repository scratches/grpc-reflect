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