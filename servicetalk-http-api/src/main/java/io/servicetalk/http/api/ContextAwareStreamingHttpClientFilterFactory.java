/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.api;

import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Publisher;

import javax.annotation.Nullable;

/**
 * API introduced to help transition 0.41 core to 0.42.
 * @deprecated DO NOT USE.
 */
@FunctionalInterface
@Deprecated
public interface ContextAwareStreamingHttpClientFilterFactory
        extends StreamingHttpClientFilterFactory {
    StreamingHttpClientFilter create(FilterableStreamingHttpClient client,
                                     @Nullable Publisher<Object> lbEventStream,
                                     @Nullable Completable sdStatus);

    @Override
    default StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
        return create(client, null, null);
    }
}
