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
package io.servicetalk.http.utils;

import io.servicetalk.client.api.GroupKey;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpClientGroup;
import io.servicetalk.http.api.HttpHeaderNames;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpRequester;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.transport.api.ExecutionContext;

import java.util.function.Function;

/**
 * An {@link HttpClientGroup} wrapper that performs automatic redirects if
 * {@link HttpClientGroup#request(GroupKey, HttpRequest)} method receives 3XX status code in the
 * {@link HttpResponse response}.
 * <p>
 * <b>Notes</b>:
 * <ul>
 *     <li>This implementation will not parse a payload for 300 (Multiple Choices) response code, if a preferred
 *     choice's URI reference is not returned in the {@link HttpHeaderNames#LOCATION Location} header.</li>
 *     <li>This implementation creates a redirect request internally with a request target in the
 *     <a href="https://tools.ietf.org/html/rfc7230#section-5.3.1">origin-form</a> and assumes that
 *     {@link HttpRequest#getRequestTarget() request target} and {@link HttpHeaderNames#HOST host header} are sufficient
 *     for a provided request-to-group-key function.</li>
 * </ul>
 *
 * @param <UnresolvedAddress> The address type used to create new {@link HttpClient}s.
 */
public final class RedirectingHttpClientGroup<UnresolvedAddress> extends DelegatingHttpClientGroup<UnresolvedAddress> {

    // https://tools.ietf.org/html/rfc2068#section-10.3 says:
    // A user agent SHOULD NOT automatically redirect a request more than 5 times,
    // since such redirections usually indicate an infinite loop.
    private static final int DEFAULT_MAX_REDIRECTS = 5;

    private final HttpRequester requester;
    private final int maxRedirects;

    /**
     * Create a new instance with the default number of follow up redirects.
     *
     * @param delegate The {@link HttpClientGroup} to delegate to.
     * @param requestToGroupKey The {@link Function} which returns the {@link GroupKey} for the given
     * {@link HttpRequest}.
     * @param executionContext The {@link ExecutionContext} to convert the {@link HttpClientGroup} to
     * an {@link HttpRequester}.
     */
    public RedirectingHttpClientGroup(final HttpClientGroup<UnresolvedAddress> delegate,
                                      final Function<HttpRequest<HttpPayloadChunk>,
                                              GroupKey<UnresolvedAddress>> requestToGroupKey,
                                      final ExecutionContext executionContext) {
        this(delegate, requestToGroupKey, executionContext, DEFAULT_MAX_REDIRECTS);
    }

    /**
     * Create a new instance.
     *
     * @param delegate The {@link HttpClientGroup} to delegate to.
     * @param requestToGroupKey The {@link Function} which returns the {@link GroupKey} for the given
     * {@link HttpRequest}.
     * @param executionContext The {@link ExecutionContext} to convert the {@link HttpClientGroup} to
     * an {@link HttpRequester}.
     * @param maxRedirects The maximum number of follow up redirects.
     */
    public RedirectingHttpClientGroup(final HttpClientGroup<UnresolvedAddress> delegate,
                                      final Function<HttpRequest<HttpPayloadChunk>,
                                              GroupKey<UnresolvedAddress>> requestToGroupKey,
                                      final ExecutionContext executionContext,
                                      final int maxRedirects) {
        super(delegate);
        requester = delegate.asRequester(requestToGroupKey, executionContext);
        this.maxRedirects = maxRedirects;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> request(final GroupKey<UnresolvedAddress> key,
                                                          final HttpRequest<HttpPayloadChunk> request) {
        final Single<HttpResponse<HttpPayloadChunk>> response = super.request(key, request);
        if (maxRedirects <= 0) {
            return response;
        }
        return new RedirectSingle(response, request, maxRedirects, requester);
    }
}
