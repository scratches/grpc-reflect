package org.springframework.grpc.reflect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GrpcClient {

	/**
	 * Alias for "service".
	 */
	@AliasFor(attribute = "service")
	String value() default "";

	String service() default "";

}