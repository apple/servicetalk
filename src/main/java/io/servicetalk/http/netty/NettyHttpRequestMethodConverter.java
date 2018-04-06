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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpRequestMethod;
import io.servicetalk.http.api.HttpRequestMethods;

import io.netty.handler.codec.http.HttpMethod;

import static io.netty.handler.codec.http.HttpMethod.valueOf;
import static io.servicetalk.http.api.HttpRequestMethods.getRequestMethod;

final class NettyHttpRequestMethodConverter {

    private NettyHttpRequestMethodConverter() {
        // No instances.
    }

    static HttpRequestMethod fromNettyHttpMethod(final HttpMethod nettyMethod) {
        return getRequestMethod(nettyMethod.name());
    }

    static HttpMethod toNettyHttpMethod(final HttpRequestMethod serviceTalkMethod) {
        if (serviceTalkMethod instanceof HttpRequestMethods) {
            switch ((HttpRequestMethods) serviceTalkMethod) {
                case GET:
                    return HttpMethod.GET;
                case HEAD:
                    return HttpMethod.HEAD;
                case OPTIONS:
                    return HttpMethod.OPTIONS;
                case TRACE:
                    return HttpMethod.TRACE;
                case PUT:
                    return HttpMethod.PUT;
                case DELETE:
                    return HttpMethod.DELETE;
                case POST:
                    return HttpMethod.POST;
                case PATCH:
                    return HttpMethod.PATCH;
                case CONNECT:
                    return HttpMethod.CONNECT;
                default:
                    break;
            }
        }
        return valueOf(serviceTalkMethod.getName());
    }
}
