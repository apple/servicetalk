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
package io.servicetalk.http.netty;

import io.servicetalk.transport.api.ProtocolConfig;

import java.util.List;
import javax.annotation.Nullable;

import static io.servicetalk.http.netty.HttpProtocolConfigs.h1Default;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

abstract class AbstractHttpConfig<TcpConfig, ReadOnlyView> {

    private final TcpConfig tcpConfig;
    @Nullable
    private H1ProtocolConfig h1Config;
    @Nullable
    private H2ProtocolConfig h2Config;
    private List<String> supportedAlpnProtocols = emptyList();

    protected AbstractHttpConfig(final TcpConfig tcpConfig) {
        this.tcpConfig = requireNonNull(tcpConfig);
        h1Config = h1Default();
    }

    protected AbstractHttpConfig(final TcpConfig tcpConfigCopy,
                                 final AbstractHttpConfig<TcpConfig, ReadOnlyView> from) {
        tcpConfig = tcpConfigCopy;
        h1Config = from.h1Config;
        h2Config = from.h2Config;
        supportedAlpnProtocols = from.supportedAlpnProtocols(); // no need to copy because this list is always immutable
    }

    final TcpConfig tcpConfig() {
        return tcpConfig;
    }

    @Nullable
    final H1ProtocolConfig h1Config() {
        return h1Config;
    }

    @Nullable
    final H2ProtocolConfig h2Config() {
        return h2Config;
    }

    final List<String> supportedAlpnProtocols() {
        return supportedAlpnProtocols;
    }

    abstract ReadOnlyView asReadOnly();

    final void protocols(final ProtocolConfig... protocols) {
        // reset current configs:
        h1Config = null;
        h2Config = null;

        for (ProtocolConfig protocol : protocols) {
            if (protocol instanceof H1ProtocolConfig) {
                h1Config((H1ProtocolConfig) protocol);
            }
            if (protocol instanceof H2ProtocolConfig) {
                h2Config((H2ProtocolConfig) protocol);
            }
        }

        if (h1Config == null && h2Config == null) {
            throw new IllegalStateException("No supported HTTP configuration provided");
        }
    }

    private void h1Config(@Nullable final H1ProtocolConfig h1Config) {
        this.h1Config = h1Config;
        if (h2Config != null) {
            if (h1Config != null) {
                supportedAlpnProtocols = unmodifiableList(asList(h2Config.alpnId(), h1Config.alpnId()));
            } else {
                supportedAlpnProtocols = singletonList(h2Config.alpnId());
            }
        } else {
            // We intentionally do not configure a list of ALPN IDs when only h1Config is provided, because it's
            // not required for HTTP/1.1 and users' environment may not support ALPN
            supportedAlpnProtocols = emptyList();
        }
    }

    private void h2Config(@Nullable final H2ProtocolConfig h2Config) {
        this.h2Config = h2Config;
        if (h1Config != null) {
            if (h2Config != null) {
                supportedAlpnProtocols = unmodifiableList(asList(h1Config.alpnId(), h2Config.alpnId()));
            } else {
                // We intentionally do not configure a list of ALPN IDs when only h1Config is provided, because it's
                // not required for HTTP/1.1 and users' environment may not support ALPN
                supportedAlpnProtocols = emptyList();
            }
        } else {
            if (h2Config != null) {
                supportedAlpnProtocols = singletonList(h2Config.alpnId());
            } else {
                supportedAlpnProtocols = emptyList();
            }
        }
    }
}
