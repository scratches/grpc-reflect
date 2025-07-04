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
package org.springframework.grpc.webflux;

import java.util.Arrays;
import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;

public abstract class GrpcCodecSupport {

	static final String GRPC_STATUS_HEADER = "grpc-status";

	static final MimeType[] MIME_TYPES = new MimeType[]{
			new MimeType("application", "grpc")
	};

	static final String DELIMITED_KEY = "delimited";

	static final String DELIMITED_VALUE = "true";

	protected boolean supportsMimeType(@Nullable MimeType mimeType) {
		if (mimeType == null) {
			return true;
		}
		for (MimeType m : MIME_TYPES) {
			if (m.isCompatibleWith(mimeType)) {
				return true;
			}
		}
		return false;
	}

	protected List<MimeType> getMimeTypes() {
		return Arrays.asList(MIME_TYPES);
	}

}
