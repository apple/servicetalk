/*
 * Copyright © 2020-2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ConnectionContext;

/**
 * {@link ConnectionContext} for HTTP.
 */
public interface HttpConnectionContext extends ConnectionContext {

    @Override
    HttpExecutionContext executionContext();

    @Override
    HttpProtocolVersion protocol();

    // Do not override the return type of parent() method to HttpConnectionContext as there is a possibility to have an
    // HttpConnectionContext over a non-HTTP ConnectionContext. Also, with HttpConnectionContext as a return type it
    // makes implementation a lot harder because we receive a non-HTTP NettyConnectionContext from the transport and
    // wrap it later.
}
