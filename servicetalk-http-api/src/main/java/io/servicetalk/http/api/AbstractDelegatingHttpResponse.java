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

import io.servicetalk.encoding.api.ContentCodec;

abstract class AbstractDelegatingHttpResponse implements HttpResponseMetaData, PayloadInfo {

    final DefaultStreamingHttpResponse original;

    AbstractDelegatingHttpResponse(final DefaultStreamingHttpResponse original) {
        this.original = original;
    }

    @Override
    public HttpProtocolVersion version() {
        return original.version();
    }

    @Deprecated
    @Override
    public ContentCodec encoding() {
        return original.encoding();
    }

    @Override
    public HttpHeaders headers() {
        return original.headers();
    }

    @Override
    public HttpResponseStatus status() {
        return original.status();
    }

    @Override
    public boolean isEmpty() {
        return original.isEmpty();
    }

    @Override
    public boolean isSafeToAggregate() {
        return original.isSafeToAggregate();
    }

    @Override
    public boolean mayHaveTrailers() {
        return original.mayHaveTrailers();
    }

    @Override
    public boolean isGenericTypeBuffer() {
        return original.isGenericTypeBuffer();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AbstractDelegatingHttpResponse that = (AbstractDelegatingHttpResponse) o;

        return original.equals(that.original);
    }

    @Override
    public int hashCode() {
        return original.hashCode();
    }

    @Override
    public String toString() {
        return original.toString();
    }
}
