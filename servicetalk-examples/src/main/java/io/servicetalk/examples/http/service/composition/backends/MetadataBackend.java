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
import io.servicetalk.examples.http.service.composition.pojo.Metadata;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.api.HttpService;
import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpSerializer;
import io.servicetalk.http.router.predicate.HttpPredicateRouterBuilder;
import io.servicetalk.transport.api.ConnectionContext;

import java.util.concurrent.ThreadLocalRandom;

import static io.servicetalk.concurrent.api.Single.success;
import static io.servicetalk.http.api.HttpResponses.newResponse;
import static io.servicetalk.http.api.HttpResponseStatuses.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static java.util.concurrent.ThreadLocalRandom.current;

/**
 * A service that returns {@link Metadata}s for an entity.
 */
final class MetadataBackend extends HttpService {

    private static final String ENTITY_ID_QP_NAME = "entityId";
    private final HttpSerializer serializer;

    private MetadataBackend(HttpSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public Single<HttpResponse<HttpPayloadChunk>> handle(final ConnectionContext ctx,
                                                         final HttpRequest<HttpPayloadChunk> request) {
        final String entityId = request.parseQuery().get(ENTITY_ID_QP_NAME);
        if (entityId == null) {
            return success(newResponse(BAD_REQUEST));
        }

        // Create random names and author for the metadata
        Metadata metadata = new Metadata(entityId, createRandomString(15), createRandomString(5));
        return success(serializer.serialize(newResponse(OK, metadata), ctx.getExecutionContext().getBufferAllocator()));
    }

    static HttpService newMetadataService(HttpSerializer serializer) {
        HttpPredicateRouterBuilder routerBuilder = new HttpPredicateRouterBuilder();
        return routerBuilder.whenPathStartsWith("/metadata")
                .thenRouteTo(new MetadataBackend(serializer))
                .build();
    }

    private String createRandomString(int size) {
        final ThreadLocalRandom random = current();
        char[] randomChars = new char[size];
        for (int i = 0; i < size; i++) {
            randomChars[i] = (char) random.nextInt(97, 122);
        }
        return new String(randomChars);
    }
}
