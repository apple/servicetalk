/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.encoding.api.ContentCodec;

import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Pass-through for pkg-private function.
 */
public final class HeaderUtilsPassThrough {

    private HeaderUtilsPassThrough() {
    }

    @Nullable
    public static ContentCodec encodingFor(final Collection<ContentCodec> allowedList,
                                    @Nullable final CharSequence name) {
        return HeaderUtils.encodingFor(allowedList, name);
    }
}
