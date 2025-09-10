/*
 * Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

package io.servicetalk.opentelemetry.http;

import io.servicetalk.http.api.HttpHeaders;
import io.servicetalk.http.api.HttpProtocolVersion;

import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Locale;
import javax.annotation.Nullable;

final class RequestHeadersPropagatorSetter implements TextMapSetter<RequestInfo> {

    static final TextMapSetter<RequestInfo> INSTANCE = new RequestHeadersPropagatorSetter();

    private RequestHeadersPropagatorSetter() {
    }

    @Override
    public void set(@Nullable final RequestInfo requestInfo, final String key, final String value) {
        if (requestInfo == null) {
            return;
        }

        // TODO: This noise is necessary because our HTTP/2 headers structure is case sensitive.
        //  Once we fix that, we can delete all the normalization noise.
        final HttpProtocolVersion httpProtocolVersion;
        if (requestInfo.connectionInfo() != null &&
                requestInfo.connectionInfo().protocol() instanceof HttpProtocolVersion) {
            httpProtocolVersion = (HttpProtocolVersion) requestInfo.connectionInfo().protocol();
        } else {
            httpProtocolVersion = requestInfo.request().version();
        }

        HttpHeaders httpHeaders = requestInfo.request().headers();
        if (httpProtocolVersion.major() >= 2) {
            httpHeaders.set(key.toLowerCase(Locale.ENGLISH), value);
        } else {
            httpHeaders.set(key, value);
        }
    }
}
