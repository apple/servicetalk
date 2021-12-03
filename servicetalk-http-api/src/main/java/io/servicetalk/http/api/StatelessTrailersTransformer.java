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
 * An implementation of {@link TrailersTransformer} that does not require any state.
 *
 * @param <Payload> Type of payload this transformer receives.
 */
public class StatelessTrailersTransformer<Payload> implements TrailersTransformer<Void, Payload> {
    @Override
    public final Void newState() {
        return null;
    }

    @Override
    public final Payload accept(final Void __, final Payload payload) {
        return accept(payload);
    }

    @Override
    public final HttpHeaders payloadComplete(final Void __, final HttpHeaders trailers) {
        return payloadComplete(trailers);
    }

    @Override
    public final HttpHeaders catchPayloadFailure(final Void __, final Throwable cause, final HttpHeaders trailers)
            throws Throwable {
        return payloadFailed(cause, trailers);
    }

    /**
     * Same as {@link #accept(Void, Object)} but without the state.
     *
     * @param payload {@link Payload} to accept.
     * @return Potentially transformed {@link Payload} instance.
     */
    protected Payload accept(final Payload payload) {
        return payload;
    }

    /**
     * Same as {@link #payloadComplete(Void, HttpHeaders)} but without the state.
     *
     * @param trailers Trailer for the streaming HTTP request/response that is transformed.
     * @return Potentially transformed trailers.
     */
    protected HttpHeaders payloadComplete(final HttpHeaders trailers) {
        return trailers;
    }

    /**
     * Same as {@link #catchPayloadFailure(Void, Throwable, HttpHeaders)} but without the state.
     *
     * @param cause of the payload stream failure.
     * @param trailers Trailer for the streaming HTTP request/response that is transformed.
     * @return Potentially transformed trailers. <strong>This will swallow the passed {@code cause}. In order to
     * propagate the {@code cause}, it should be re-thrown.</strong>
     * @throws Throwable If the error has to be propagated
     */
    protected HttpHeaders payloadFailed(@SuppressWarnings("unused") final Throwable cause,
                                        @SuppressWarnings("unused") final HttpHeaders trailers) throws Throwable {
        throw cause;
    }
}
