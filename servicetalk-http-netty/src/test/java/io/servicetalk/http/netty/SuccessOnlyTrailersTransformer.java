/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.StatelessTrailersTransformer;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

final class SuccessOnlyTrailersTransformer<T> extends StatelessTrailersTransformer<T> {

    private final UnaryOperator<HttpHeaders> onComplete;

    SuccessOnlyTrailersTransformer(final UnaryOperator<HttpHeaders> onComplete) {
        this.onComplete = requireNonNull(onComplete);
    }

    @Override
    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
        return onComplete.apply(trailers);
    }
}
