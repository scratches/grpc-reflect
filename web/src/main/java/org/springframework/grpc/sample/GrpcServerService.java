package org.springframework.grpc.sample;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.grpc.sample.proto.HelloReply;
import org.springframework.grpc.sample.proto.HelloRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
public class GrpcServerService {

	private static Log log = LogFactory.getLog(GrpcServerService.class);

	@PostMapping(path = "Simple/SayHello", produces = "application/grpc")
	public HelloReply sayHello(@RequestBody HelloRequest req) {
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
		return response;
	}

	// TODO: why does this one have to use ResponseEntity?
	@PostMapping("Simple/StreamHello")
	public ResponseEntity<ResponseBodyEmitter> streamHello(@RequestBody HelloRequest req) {
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException();
		}
		ResponseBodyEmitter emitter = new ResponseBodyEmitter();
		Thread thread = new Thread(new ResponseStreamer(req.getName(), emitter));
		thread.setName("ResponseStreamer-" + req.getName());
		thread.start();
		return ResponseEntity.ok().contentType(MediaType.valueOf("application/grpc")).body(emitter);
	}

	class ResponseStreamer implements Runnable {

		private final String name;
		private final ResponseBodyEmitter responseObserver;

		ResponseStreamer(String name, ResponseBodyEmitter responseObserver) {
			this.name = name;
			this.responseObserver = responseObserver;
		}

		@Override
		public void run() {
			log.info("Hello " + name);
			int count = 0;
			while (count < 10) {
				HelloReply reply = HelloReply.newBuilder().setMessage("Hello(" + count + ") ==> " + name).build();
				try {
					responseObserver.send(reply, MediaType.valueOf("application/grpc"));
					count++;
					Thread.sleep(1000L);
				} catch (IOException e) {
					responseObserver.completeWithError(e);
					return;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					responseObserver.completeWithError(e);
					return;
				}
			}
			responseObserver.complete();

		}

	}

}