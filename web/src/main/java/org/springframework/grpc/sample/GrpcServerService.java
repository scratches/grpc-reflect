package org.springframework.grpc.sample;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GrpcServerService {

	private static Log log = LogFactory.getLog(GrpcServerService.class);

	@PostMapping("Simple/SayHello")
	public ResponseEntity<HelloReply> sayHello(@RequestBody HelloRequest req) {
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
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/grpc")).body(response);
	}

}