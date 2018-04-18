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
package io.servicetalk.http.router.jersey;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

/**
 * Feature enabling ServiceTalk request handling.
 * This feature registers providers and binders needed to enable ServiceTalk as a handler for a Jersey application.
 */
public final class ServiceTalkFeature implements Feature {
    @Override
    public boolean configure(final FeatureContext context) {
        context.register(PublisherMessageBodyReaderWriter.class);
        return true;
    }
}
