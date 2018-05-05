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

import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.transport.api.ExecutionContext;
import io.servicetalk.transport.netty.internal.Connection;

import static io.servicetalk.transport.api.FlushStrategy.flushBeforeEnd;

final class NonPipelinedHttpConnection extends AbstractHttpConnection<Connection<Object, Object>> {

    NonPipelinedHttpConnection(Connection<Object, Object> connection,
                               ReadOnlyHttpClientConfig config,
                               ExecutionContext executionContext) {
        super(connection, config, executionContext);
    }

    @Override
    protected Publisher<Object> writeAndRead(final Publisher<Object> requestStream) {
        // TODO flush strategy needs to be configurable
        return connection.write(requestStream, flushBeforeEnd()).andThen(connection.read());
    }
}
