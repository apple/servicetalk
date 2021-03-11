/*
 * Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.transport.api.ClientSslConfig;

import javax.annotation.Nullable;

class DelegatingClientSslConfig extends DelegatingSslConfig<ClientSslConfig> implements ClientSslConfig {
    DelegatingClientSslConfig(final ClientSslConfig delegate) {
        super(delegate);
    }

    @Nullable
    @Override
    public String hostnameVerificationAlgorithm() {
        return delegate().hostnameVerificationAlgorithm();
    }

    @Nullable
    @Override
    public String peerHost() {
        return delegate().peerHost();
    }

    @Override
    public int peerPort() {
        return delegate().peerPort();
    }

    @Nullable
    @Override
    public String sniHostname() {
        return delegate().sniHostname();
    }
}
