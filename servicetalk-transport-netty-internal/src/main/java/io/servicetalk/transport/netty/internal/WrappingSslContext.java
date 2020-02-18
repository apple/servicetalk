/*
 * Copyright © 2018-2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.transport.netty.internal;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;

import java.util.List;
import javax.annotation.Nullable;
import javax.net.ssl.SSLEngine;

final class WrappingSslContext extends DelegatingSslContext {
    @Nullable
    private final String[] protocols;

    WrappingSslContext(SslContext context, @Nullable List<String> protocols) {
        super(context);
        this.protocols = protocols == null ? null : protocols.toArray(new String[0]);
    }

    @Override
    protected void initEngine(SSLEngine engine) {
        if (protocols != null) {
            engine.setEnabledProtocols(protocols);
        }
    }
}
