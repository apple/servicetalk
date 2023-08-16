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

  @Override
  public String getNetworkProtocolName(HttpRequestMetaData request, @Nullable HttpResponseMetaData response) {
    return "http";
  }

  @Override
  public String getNetworkProtocolVersion(HttpRequestMetaData request,
                                          @Nullable HttpResponseMetaData response) {
    if (response != null) {
      return response.version().fullVersion();
    }
    return request.version().fullVersion();
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequestMetaData request) {
    HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
    return effectiveHostAndPort != null ? effectiveHostAndPort.hostName() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(HttpRequestMetaData request) {
    HostAndPort effectiveHostAndPort = request.effectiveHostAndPort();
    return effectiveHostAndPort != null ? effectiveHostAndPort.port() : null;
  }
}
