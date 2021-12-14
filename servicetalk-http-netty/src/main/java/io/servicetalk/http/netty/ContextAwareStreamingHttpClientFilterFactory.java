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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.http.api.FilterableStreamingHttpClient;
import io.servicetalk.http.api.StreamingHttpClientFilter;
import io.servicetalk.http.api.StreamingHttpClientFilterFactory;
import io.servicetalk.http.netty.DefaultSingleAddressHttpClientBuilder.SdStatusCompletable;

import javax.annotation.Nullable;

@FunctionalInterface
interface ContextAwareStreamingHttpClientFilterFactory extends StreamingHttpClientFilterFactory {

    StreamingHttpClientFilter create(FilterableStreamingHttpClient client, @Nullable Publisher<Object> lbEventStream,
                                     @Nullable SdStatusCompletable sdStatus);

    @Override
    default StreamingHttpClientFilter create(FilterableStreamingHttpClient client) {
        return create(client, null, null);
    }
}
