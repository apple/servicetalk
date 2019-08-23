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
package io.servicetalk.http.api;

/**
 * A contract for transforming trailers for a streaming HTTP request/response.
 *
 * @param <State> Type of state provided by this transformer.
 * @param <Payload> Type of payload this transformer receives.
 */
public interface TrailersTransformer<State, Payload> {

    /**
     * Creates a new instance of the {@link State}.
     *
     * @return A new instance of the {@link State}.
     */
    State newState();

    /**
     * Accepts a {@link Payload}.
     *
     * @param state {@link State} instance created previously by this transformer.
     * @param payload {@link Payload} to accept.
     * @return Potentially transformed {@link Payload} instance.
     */
    Payload accept(State state, Payload payload);

    /**
     * Invoked once all {@link Payload} instances are {@link #accept(Object, Object) accepted} and the payload stream
     * has successfully completed.
     *
     * @param state {@link State} instance created previously by this transformer.
     * @param trailers Trailer for the streaming HTTP request/response that is transformed.
     * @return Potentially transformed trailers.
     */
    HttpHeaders payloadComplete(State state, HttpHeaders trailers);

    /**
     * Invoked once all {@link Payload} instances are {@link #accept(Object, Object) accepted} and the payload stream
     * has terminated with an error.
     *
     * @param state {@link State} instance created previously by this transformer.
     * @param cause of the payload stream failure.
     * @param trailers Trailer for the streaming HTTP request/response that is transformed.
     * @return Potentially transformed trailers.
     */
    HttpHeaders payloadFailed(State state, Throwable cause, HttpHeaders trailers);
}
