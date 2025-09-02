package org.springframework.grpc.sample;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.grpc.reflect.GrpcController;
import org.springframework.grpc.reflect.GrpcMapping;

@GrpcController("Simple")
public class GrpcServerService {

	private static Log log = LogFactory.getLog(GrpcServerService.class);

	@GrpcMapping
	public Hello sayHello(Hello req) {
		log.info("Hello " + req.getName());
		if (req.getName().startsWith("error")) {
			throw new IllegalArgumentException("Bad name: " + req.getName());
		}
		if (req.getName().startsWith("internal")) {
			throw new RuntimeException();
		}
		Hello response = new Hello();
		response.setName("Hello ==> " + req.getName());
		return response;
	}

}