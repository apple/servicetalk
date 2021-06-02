/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.netty;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpRequest;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.api.StreamingHttpResponseFactory;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.api.StreamingHttpServiceFilter;
import io.servicetalk.http.api.StreamingHttpServiceFilterFactory;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter;
import io.servicetalk.http.utils.auth.BasicAuthHttpServiceFilter.CredentialsVerifier;

import org.junit.jupiter.api.Test;

import static io.servicetalk.concurrent.api.Completable.completed;
import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.FilterFactoryUtils.appendServiceFilterFactory;
import static io.servicetalk.http.api.HttpHeaderNames.AUTHORIZATION;
import static io.servicetalk.http.netty.AsyncContextHttpFilterVerifier.verifyServerFilterAsyncContextVisibility;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Base64.getEncoder;
import static java.util.Objects.requireNonNull;

class BasicAuthHttpServiceFilterTest {

    private static final class BasicUserInfo {

        private final String userId;

        BasicUserInfo(final String userId) {
            this.userId = requireNonNull(userId);
        }

        public String userId() {
            return userId;
        }
    }

    private static final String REALM_VALUE = "hw_realm";

    @Test
    void verifyAsyncContext() throws Exception {
        verifyServerFilterAsyncContextVisibility(appendServiceFilterFactory(
                new StreamingHttpServiceFilterFactory() {
                    @Override
                    public StreamingHttpServiceFilter create(final StreamingHttpService service) {
                        return new StreamingHttpServiceFilter(service) {
                            @Override
                            public Single<StreamingHttpResponse> handle(
                                    final HttpServiceContext ctx, final StreamingHttpRequest request,
                                    final StreamingHttpResponseFactory responseFactory) {
                                request.headers().set(AUTHORIZATION, "Basic " + userPassBase64());
                                return super.handle(ctx, request, responseFactory);
                            }
                        };
                    }
                },
                new BasicAuthHttpServiceFilter.Builder<>(new CredentialsVerifier<Object>() {
                    @Override
                    public Completable closeAsync() {
                        return completed();
                    }

                    @Override
                    public Single<Object> apply(final String userId, final String password) {
                        return succeeded(new BasicUserInfo(userId));
                    }
                }, REALM_VALUE).buildServer()));
    }

    private static String userPassBase64() {
        return getEncoder().encodeToString("user:pass".getBytes(ISO_8859_1));
    }
}
