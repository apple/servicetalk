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
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionStreamingService implements StreamingHttpService {
    private final Method method;

    public ReflectionStreamingService(final Method method) {
        this.method = method;
    }

    @Override
    public Single<StreamingHttpResponse> handle(final HttpServiceContext ctx, final StreamingHttpRequest request,
                                                final StreamingHttpResponseFactory responseFactory) {
        try {
            return (Single<StreamingHttpResponse>) method.invoke(ctx, request, responseFactory);
        } catch (IllegalAccessException | InvocationTargetException e) {
           return Single.failed(e);
        }
    }
}
