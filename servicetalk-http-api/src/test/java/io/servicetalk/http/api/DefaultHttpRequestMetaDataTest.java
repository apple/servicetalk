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
package io.servicetalk.http.api;

import java.util.List;
import java.util.Map;

import static io.servicetalk.http.api.DefaultHttpHeadersFactory.INSTANCE;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_1;
import static io.servicetalk.http.api.HttpRequestMethod.GET;

public class DefaultHttpRequestMetaDataTest extends AbstractHttpRequestMetaDataTest<DefaultHttpRequestMetaData> {

    @Override
    protected void createFixture(final String uri) {
        fixture = new DefaultHttpRequestMetaData(GET, uri, HTTP_1_1, INSTANCE.newHeaders());
    }

    @Override
    protected void createFixture(final String uri, final HttpRequestMethod method) {
        fixture = new DefaultHttpRequestMetaData(method, uri, HTTP_1_1, INSTANCE.newHeaders());
    }

    @Override
    protected void setFixtureQueryParams(final Map<String, List<String>> params) {
        fixture.setQueryParams(params);
    }
}
