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
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import javax.annotation.Nullable;

import static io.servicetalk.http.api.HttpSerializationProviders.jsonSerializer;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class BodyParameter implements Parameter {

    private static final HttpSerializationProvider jsonSerializer = jsonSerializer(new JacksonSerializationProvider());

    @Nullable
    private final Class<?> classType;
    private final String expectedContentType;

    public BodyParameter(Type type, String expectedContentType) {
        this.expectedContentType = expectedContentType;

        final ParameterizedType parameterizedType = (ParameterizedType) type;
        Type typeArgument = null;
        final Type rawType = parameterizedType.getRawType();

        final boolean ok = rawType instanceof Class &&
                (typeArgument = getSingleTypeArgumentOrNull(parameterizedType)) != null &&
                typeArgument instanceof Class;
        this.classType = ok ? (Class) typeArgument : null;
    }

    @Nullable
    private static Type getSingleTypeArgumentOrNull(final ParameterizedType parameterizedType) {
        final Type[] typeArguments = parameterizedType.getActualTypeArguments();
        return typeArguments.length != 1 ? null : typeArguments[0];
    }

    @Nullable
    @Override
    public Object get(final HttpServiceContext ctx, final StreamingHttpRequest request,
                      final StreamingHttpResponseFactory responseFactory) {
        final HttpHeaders headers = request.headers();
        final CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (!Objects.equals(contentType, expectedContentType)) {
            return Single.failed(new RuntimeException("Wrong content type. Expecting " + expectedContentType + "."));
        }

        if (!APPLICATION_JSON.equals(expectedContentType)) {
            return Single.failed(new RuntimeException("Wrong content type. Expecting " + APPLICATION_JSON + "."));
        }
        try {
            return jsonSerializer.deserializerFor(classType).deserialize(headers, request.payloadBody());
        } catch (Exception e) {
            return Single.failed(e).toPublisher();
        }
    }
}
