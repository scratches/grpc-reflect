package org.springframework.grpc.sample;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
public class GrpcServerService {

	private static Log log = LogFactory.getLog(GrpcServerService.class);

	@PostMapping("Simple/SayHello")
	public ResponseEntity<Flux<HelloReply>> sayHello(@RequestBody HelloRequest req) {
		log.info("Hello " + req.getName());
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException();
		}
		HelloReply response = HelloReply.newBuilder()
				.setMessage("Hello ==> " + req.getName())
				.build();
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/grpc")).body(Flux.just(response));
	}

	@PostMapping("Simple/StreamHello")
	public ResponseEntity<Flux<HelloReply>> streamHello(@RequestBody HelloRequest req) {
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException();
		}
		Flux<HelloReply> emitter = Flux.interval(Duration.ofSeconds(1)).map(i -> {
			HelloReply reply = HelloReply.newBuilder()
					.setMessage("Hello(" + i + ") ==> " + req.getName())
					.build();
			return reply;
		}).take(10);
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/grpc")).body(emitter);
	}

}