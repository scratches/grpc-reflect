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

package org.springframework.grpc.webmvc;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StreamUtils;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;

/**
 * HTTP message converter for JSON to gRPC protocol buffer messages in Spring
 * WebMVC.
 * <p>
 * This converter extends {@link AbstractHttpMessageConverter} to provide
 * conversion
 * capabilities between HTTP requests/responses and gRPC {@link Message} objects
 * in
 * traditional servlet-based Spring applications.
 *
 * @author Dave Syer
 * @since 1.0.0
 */
public class GrpcJsonHttpMessageConverter extends AbstractHttpMessageConverter<Message> {

	/**
	 * The default charset used by the converter.
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	/**
	 * The media-type for JSON {@code application/json}.
	 */
	public static final MediaType JSON = MediaType.APPLICATION_JSON;

	private static final Map<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap<>();

	public GrpcJsonHttpMessageConverter() {
		super(JSON);
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return Message.class.isAssignableFrom(clazz);
	}

	@Override
	protected MediaType getDefaultContentType(Message message) {
		return JSON;
	}

	@Override
	protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		MediaType contentType = inputMessage.getHeaders().getContentType();
		if (contentType == null) {
			contentType = JSON;
		}
		Charset charset = contentType.getCharset();
		if (charset == null) {
			charset = DEFAULT_CHARSET;
		}

		Message.Builder builder = getMessageBuilder(clazz);
		JsonFormat.parser().merge(new String(StreamUtils.copyToByteArray(inputMessage.getBody()), charset), builder);
		return builder.build();
	}

	/**
	 * Create a new {@code Message.Builder} instance for the given class.
	 * <p>
	 * This method uses a ConcurrentReferenceHashMap for caching method lookups.
	 */
	private Message.Builder getMessageBuilder(Class<? extends Message> clazz) {
		try {
			Method method = methodCache.get(clazz);
			if (method == null) {
				method = clazz.getMethod("newBuilder");
				methodCache.put(clazz, method);
			}
			return (Message.Builder) method.invoke(clazz);
		} catch (Exception ex) {
			throw new HttpMessageConversionException(
					"Invalid Protobuf Message type: no invocable newBuilder() method on " + clazz, ex);
		}
	}

	@Override
	protected boolean canWrite(@Nullable MediaType mediaType) {
		return super.canWrite(mediaType);
	}

	@Override
	protected void writeInternal(Message message, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {

		MediaType contentType = outputMessage.getHeaders().getContentType();
		if (contentType == null) {
			contentType = getDefaultContentType(message);
			Assert.state(contentType != null, "No content type");
		}
		Charset charset = contentType.getCharset();
		if (charset == null) {
			charset = DEFAULT_CHARSET;
		}

		outputMessage.getBody()
				.write(JsonFormat.printer().omittingInsignificantWhitespace().print(message).getBytes(charset));

	}

	@Override
	protected boolean supportsRepeatableWrites(Message message) {
		return true;
	}

}
