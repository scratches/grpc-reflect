/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class GrpcExceptionHandler implements HandlerExceptionResolver, Ordered {

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		if (supportsMimeType(request.getContentType())) {
			response.setContentType("application/grpc");
			addTrailer(response);
			return new ModelAndView(); // No view to render, just complete the response
		}
		return null;
	}

	private void addTrailer(HttpServletResponse response) {
		response.setTrailerFields(() -> {
			// gRPC requires a trailer with the message status
			return Map.of("grpc-status", "13"); // INTERNAL error code
		});
	}

	private boolean supportsMimeType(String contentType) {
		if (contentType == null) {
			return false;
		}
		if (MediaType.valueOf("application/grpc").isCompatibleWith(MediaType.parseMediaType(contentType))) {
			return true;
		}
		return false;
	}

}