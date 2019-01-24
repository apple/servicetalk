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
package io.servicetalk.concurrent.api;

import static java.lang.System.identityHashCode;

final class AsyncContextMapUtils {
    private AsyncContextMapUtils() {
        // no instances
    }

    static String contextMapToString(AsyncContextMap map) {
        final String simpleName = map.getClass().getSimpleName();
        // 13 is 2 characters for () + 11 hash code integer
        // assume size of 25 (5 overhead characters for formatting) for each key/value pair
        StringBuilder sb = new StringBuilder(simpleName.length() + 13 + map.size() * 25);
        sb.append(simpleName)
          .append('(')
          // there are many copies of these maps around, the content maybe equal but a differentiating factor is the
          // object reference. this may help folks understand why state is not visible across AsyncContext boundaries.
          .append(identityHashCode(map))
          .append(')');

        map.forEach((key, value) -> {
            sb.append(" {").append(key).append(", ").append(value).append('}');
            return true;
        });
        return sb.toString();
    }
}
