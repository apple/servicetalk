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

import io.servicetalk.http.api.HttpDeserializer;
import io.servicetalk.http.api.HttpSerializationProviders;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.util.List;
import java.util.Map;

final class FormParameter implements Parameter {

    private static final HttpDeserializer<Map<String, List<String>>> formDeserializer =
            HttpSerializationProviders.formUrlEncodedDeserializer();

    private final String name;

    FormParameter(final String name) {
        this.name = name;
    }

    @Override
    public Object get(final HttpServiceContext ctx, final StreamingHttpRequest request,
                      final StreamingHttpResponseFactory responseFactory) {

        return request.payloadBody(formDeserializer)
                .map(params -> params.get(name))
                .firstOrElse(() -> null);
    }
}
