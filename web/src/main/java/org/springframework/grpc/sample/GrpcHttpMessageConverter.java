package org.springframework.grpc.sample;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.DelegatingServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StreamUtils;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;

import jakarta.servlet.http.HttpServletResponse;

public class GrpcHttpMessageConverter extends AbstractHttpMessageConverter<Message> {

	/**
	 * The default charset used by the converter.
	 */
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	/**
	 * The media-type for protobuf {@code application/x-protobuf}.
	 */
	public static final MediaType GRPC = new MediaType("application", "grpc", DEFAULT_CHARSET);

	/**
	 * The HTTP header containing the protobuf schema.
	 */
	public static final String X_PROTOBUF_SCHEMA_HEADER = "X-Protobuf-Schema";

	/**
	 * The HTTP header containing the protobuf message.
	 */
	public static final String X_PROTOBUF_MESSAGE_HEADER = "X-Protobuf-Message";

	private static final Map<Class<?>, Method> methodCache = new ConcurrentReferenceHashMap<>();

	final ExtensionRegistry extensionRegistry;

	/**
	 * Construct a new {@code ProtobufHttpMessageConverter}.
	 */
	public GrpcHttpMessageConverter() {
		this(null);
	}

	/**
	 * Construct a new {@code GrpcHttpMessageConverter} with a registry that
	 * specifies protocol message extensions.
	 * 
	 * @param extensionRegistry the registry to populate
	 */
	public GrpcHttpMessageConverter(ExtensionRegistry extensionRegistry) {
		setSupportedMediaTypes(List.of(GRPC));
		this.extensionRegistry = (extensionRegistry == null ? ExtensionRegistry.newInstance() : extensionRegistry);
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return Message.class.isAssignableFrom(clazz);
	}

	@Override
	protected MediaType getDefaultContentType(Message message) {
		return GRPC;
	}

	@Override
	protected Message readInternal(Class<? extends Message> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		MediaType contentType = inputMessage.getHeaders().getContentType();
		if (contentType == null) {
			contentType = GRPC;
		}
		Charset charset = contentType.getCharset();
		if (charset == null) {
			charset = DEFAULT_CHARSET;
		}

		Message.Builder builder = getMessageBuilder(clazz);
		byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
		// gRPC requires a 5-byte header
		if (body.length < 5) {
			throw new HttpMessageConversionException("gRPC message too short: " + body.length);
		}
		ByteBuffer wrapped = ByteBuffer.wrap(body); // big-endian by default
		int length = wrapped.getInt(1); // read the length from the 2nd to 5th byte
		if (length != body.length - 5) {
			throw new HttpMessageConversionException("gRPC message length mismatch: expected " + length +
					" but got " + (body.length - 5));
		}
		body = Arrays.copyOfRange(body, 5, body.length);
		builder.mergeFrom(body, this.extensionRegistry);
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

		setProtoHeader(outputMessage, message);
		outputMessage.getHeaders().set("Grpc-Encoding", "identity");
		outputMessage.getHeaders().set("Grpc-Accept-Encoding", "identity");
		
		CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(outputMessage.getBody());
		if (GRPC.isCompatibleWith(contentType)) {
			// gRPC requires a 5-byte header
			codedOutputStream.writeRawByte(0x00); // 1 byte for the compression flag
			byte[] buffer = new byte[4];
			ByteBuffer wrapped = ByteBuffer.wrap(buffer);
			wrapped.order(ByteOrder.BIG_ENDIAN); // gRPC uses big-endian
			wrapped.putInt(0, message.getSerializedSize()); // write the length in big-endian
			codedOutputStream.writeRawBytes(buffer); // 4 bytes for the length
		}
		message.writeTo(codedOutputStream);
		if (GRPC.isCompatibleWith(contentType)) {
			// gRPC requires a trailer
			setTrailers(outputMessage, message);
		}
		codedOutputStream.flush();
	
	}

	private void setTrailers(HttpOutputMessage outputMessage, Message message) {
		HttpServletResponse servlet = getServletResponse(outputMessage);
		if (servlet != null) {
			servlet.setTrailerFields(() -> {
				// gRPC requires a trailer with the message status
				return Map.of("grpc-status", status(servlet.getStatus()));
			});
		}
	}

	private HttpServletResponse getServletResponse(HttpOutputMessage outputMessage) {
		HttpServletResponse servlet = null;
		if (outputMessage instanceof ServletServerHttpResponse response) {
			servlet = response.getServletResponse();
		} else if (outputMessage instanceof DelegatingServerHttpResponse response) {
			if (response.getDelegate() instanceof ServletServerHttpResponse servletResponse) {
				servlet = servletResponse.getServletResponse();
			}
		}
		return servlet;
	}

	private String status(int status) {
		if (status >= 200 && status < 300) {
			return "0"; // OK
		} else if (status == 400) {
			return "3"; // INVALID_ARGUMENT
		} else if (status == 401) {
			return "16"; // UNAUTHENTICATED
		} else if (status == 403) {
			return "7"; // PERMISSION_DENIED
		} else if (status == 404) {
			return "5"; // NOT_FOUND
		} else if (status == 408) {
			return "4"; // DEADLINE_EXCEEDED
		} else if (status == 429) {
			return "8"; // RESOURCE_EXHAUSTED
		} else if (status == 501) {
			return "12"; // UNIMPLEMENTED
		} else if (status == 503) {
			return "14"; // UNAVAILABLE
		} else if (status >= 500 && status < 600) {
			return "13"; // INTERNAL
		}
		return "2"; // UNKNOWN
	}

	/**
	 * Set the "X-Protobuf-*" HTTP headers when responding with a message of
	 * content type "application/x-protobuf"
	 * <p>
	 * <b>Note:</b> <code>outputMessage.getBody()</code> should not have been called
	 * before because it writes HTTP headers (making them read only).
	 * </p>
	 */
	private void setProtoHeader(HttpOutputMessage response, Message message) {
		response.getHeaders().set(X_PROTOBUF_SCHEMA_HEADER, message.getDescriptorForType().getFile().getName());
		response.getHeaders().set(X_PROTOBUF_MESSAGE_HEADER, message.getDescriptorForType().getFullName());
	}

	@Override
	protected boolean supportsRepeatableWrites(Message message) {
		return true;
	}

}