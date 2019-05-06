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
package io.servicetalk.examples.http.service.composition.backends;

import io.servicetalk.concurrent.api.Single;
import io.servicetalk.examples.http.service.composition.pojo.User;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpResponseFactory;
import io.servicetalk.http.api.HttpSerializationProvider;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpServiceContext;
import io.servicetalk.http.api.StreamingHttpService;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.examples.http.service.composition.backends.StringUtils.randomString;

/**
 * A service that returns {@link User} for an entity.
 */
public final class UserBackend implements HttpService {

    public static final String USER_ID_QP_NAME = "userId";

    private final HttpSerializationProvider serializer;

    private UserBackend(HttpSerializationProvider serializer) {
        this.serializer = serializer;
    }

    @Override
    public Single<HttpResponse> handle(HttpServiceContext ctx, HttpRequest request,
                                       HttpResponseFactory responseFactory) {
        final String userId = request.queryParameter(USER_ID_QP_NAME);
        if (userId == null) {
            return succeeded(responseFactory.badRequest());
        }

        // Create a random rating
        User user = new User(userId, randomString(5), randomString(3));
        return succeeded(responseFactory.ok().payloadBody(user, serializer.serializerFor(User.class)));
    }

    static StreamingHttpService newUserService(HttpSerializationProvider serializer) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        return routerBuilder.whenPathStartsWith("/user")
                .thenRouteTo(new UserBackend(serializer))
                .buildStreaming();
    }
}
