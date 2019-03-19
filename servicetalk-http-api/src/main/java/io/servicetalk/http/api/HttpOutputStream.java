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

import java.io.OutputStream;

/**
 * The {@link OutputStream} which provides access to the HTTP
 * <a href="https://tools.ietf.org/html/rfc7230#section-4.4">trailers</a>.
 */
public abstract class HttpOutputStream extends OutputStream implements TrailersHolder {

    @Override
    public HttpOutputStream addTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.addTrailer(name, value);
        return this;
    }

    @Override
    public HttpOutputStream addTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.addTrailers(trailers);
        return this;
    }

    @Override
    public HttpOutputStream setTrailer(final CharSequence name, final CharSequence value) {
        TrailersHolder.super.setTrailer(name, value);
        return this;
    }

    @Override
    public HttpOutputStream setTrailers(final HttpHeaders trailers) {
        TrailersHolder.super.setTrailers(trailers);
        return this;
    }
}
