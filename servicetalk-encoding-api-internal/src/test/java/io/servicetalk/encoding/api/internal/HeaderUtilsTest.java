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
package io.servicetalk.encoding.api.internal;

import io.servicetalk.encoding.api.ContentCodec;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.encoding.api.Identity.identity;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class HeaderUtilsTest {

    @Test
    void negotiateAcceptedEncoding() {
        List<ContentCodec> clientSupportedEncodings = asList(identity(), new NoopContentCodec("any"));
        List<ContentCodec> serverSupportedEncodings = singletonList(new CustomIdentityContentCodec());
        ContentCodec acceptedEncoding = HeaderUtils.negotiateAcceptedEncoding(clientSupportedEncodings,
                serverSupportedEncodings);
        assertThat(acceptedEncoding, is(nullValue()));
    }
}
