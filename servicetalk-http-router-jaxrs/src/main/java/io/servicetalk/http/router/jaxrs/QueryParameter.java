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

import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponseFactory;

import java.lang.reflect.Array;
import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.http.router.jaxrs.ParameterUtil.castTo;
import static io.servicetalk.http.router.jaxrs.ParameterUtil.toList;

public class QueryParameter implements Parameter {
    @Nullable
    private final String defaultValue;
    private final String name;
    private final Class<?> type;

    public QueryParameter(String name, Class<?> type, @Nullable String defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    @Override
    public Object get(final HttpServiceContext ctx, final StreamingHttpRequest request,
                      final StreamingHttpResponseFactory responseFactory) {
        List<String> params = toList(request.queryParameters(name));

        if (params.isEmpty()) {
            return defaultValue;
        }

        if (type == List.class) {
            return params;
        }

        if (type.isArray()) {
            if (type.getComponentType() == String.class) {
                return params.toArray((String[]) Array.newInstance(type.getComponentType(), params.size()));
            } else {
                throw new IllegalStateException("Not supported.");
            }
        }

        return castTo(type, params.get(0));
    }
}
