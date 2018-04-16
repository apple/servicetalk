/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.predicate;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponses;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.transport.api.ConnectionContext;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_LENGTH;
import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatuses.NOT_FOUND;

final class DefaultFallbackService<I, O> implements HttpService<I, O> {

    private static final DefaultFallbackService INSTANCE = new DefaultFallbackService();

    private DefaultFallbackService() {
        // singleton
    }

    @Override
    public Single<HttpResponse<O>> handle(final ConnectionContext ctx, final HttpRequest<I> request) {
        final HttpResponse<O> response = HttpResponses.newResponse(request.getVersion(), NOT_FOUND);
        response.getHeaders().set(CONTENT_LENGTH, "0")
                .set(CONTENT_TYPE, TEXT_PLAIN);
        // TODO(derek): Set keepalive once we have an isKeepAlive helper method.
        return Single.success(response);
    }

    @SuppressWarnings("unchecked")
    static <I, O> HttpService<I, O> instance() {
        return INSTANCE;
    }
}
