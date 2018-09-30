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
package io.servicetalk.opentracing.http;

import io.opentracing.Scope;

import static io.opentracing.tag.Tags.ERROR;

final class TracingUtils {
    private TracingUtils() {
        // no instances
    }

    static void tagErrorAndClose(Scope currentScope) {
        ERROR.set(currentScope.span(), true);
        currentScope.close();
    }
}
