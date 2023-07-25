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

import io.servicetalk.http.api.HttpProtocolVersion;
import io.servicetalk.http.api.HttpRequestMetaData;
import io.servicetalk.http.api.HttpResponseMetaData;
import io.servicetalk.transport.api.HostAndPort;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class ServicetalkNetServerAttributesGetter
    implements NetServerAttributesGetter<HttpRequestMetaData, HttpResponseMetaData> {

  static final ServicetalkNetServerAttributesGetter INSTANCE = new ServicetalkNetServerAttributesGetter();

  private ServicetalkNetServerAttributesGetter() {
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequestMetaData request, @Nullable HttpResponseMetaData response) {

    if (response == null) {
      return null;
    }
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpRequestMetaData request,
                                          @Nullable HttpResponseMetaData response) {
    HttpProtocolVersion version = request.version();
    if (version.major() == 1) {
      if (version.minor() == 1) {
        return "1.1";
      }
      if (version.minor() == 0) {
        return "1.0";
      }
    } else if (version.major() == 2 && version.minor() == 0) {
      return "2.0";
    }
    return version.major() + "." + version.minor();
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequestMetaData request) {
    HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
    return effectiveHostAndPort != null ? effectiveHostAndPort.hostName() : null;
  }

  @Override
  public Integer getServerPort(HttpRequestMetaData request) {
    HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
    return effectiveHostAndPort != null ? effectiveHostAndPort.port() : null;
  }
}
