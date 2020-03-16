/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.http.router.jaxrs;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.data.jackson.JacksonSerializationProvider;
import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpSerializationProviders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;

public class BodyParameter extends Parameter {

    private static final Logger log = LoggerFactory.getLogger(BodyParameter.class);
    private static final HttpDeserializer<String> textDeserializer = HttpSerializationProviders.textDeserializer();
    private static final HttpSerializationProvider jsonSerializer = jsonSerializer(new JacksonSerializationProvider());

    private final String expectedContentType;

    public BodyParameter(String name, Class<?> type, String expectedContentType) {
        super(name, type);
        this.expectedContentType = expectedContentType;
    }

    @Nullable
    @Override
    public Object get(final HttpServiceContext ctx, final StreamingHttpRequest request,
                      final StreamingHttpResponseFactory responseFactory) {
        final CharSequence contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.equals(expectedContentType)) {
            throw new RuntimeException("Wrong content type. Expecting " + expectedContentType + ".");
        }

        switch (expectedContentType) {
            case MediaType.APPLICATION_JSON :
                Single<String> data;
                try {
                    data = request.payloadBody(textDeserializer).firstOrElse(() -> null);
                    if (type == String.class) {
                        return data;
                    }
                    return request.payloadBody(jsonSerializer.deserializerFor(type));
                } catch (Exception e) {
                    log.error("Unexpected error during parsing body param.", e);
                    throw new RuntimeException("Unexpected error during parsing body param.", e);
                }
            default :
                return request.payloadBody(textDeserializer).firstOrElse(() -> null);
        }
    }
}
