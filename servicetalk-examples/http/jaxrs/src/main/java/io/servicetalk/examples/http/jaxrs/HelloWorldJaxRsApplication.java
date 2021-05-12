/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.http.jaxrs;

import org.glassfish.jersey.media.multipart.MultiPartFeature;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

/**
 * JAX-RS Hello World {@link Application}.
 */
public class HelloWorldJaxRsApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return new HashSet<>(asList(
                MultiPartFeature.class,
                HelloWorldJaxRsResource.class
            )
        );
    }
}
