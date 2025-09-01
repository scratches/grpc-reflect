package org.springframework.grpc.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.grpc.reflect.EnableGrpcMapping;
import org.springframework.grpc.reflect.GrpcController;
import org.springframework.grpc.reflect.GrpcMapping;

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

	static class Input {
	}
	
	static class Output {
	}
	
}
