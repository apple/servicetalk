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
package io.servicetalk.grpc.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

class DeflateGrpcMessageCodec extends ZipGrpcMessageCodec {

    @Override
    DeflaterOutputStream newCodecOutputStream(final OutputStream out) {
        return new DeflaterOutputStream(out);
    }

    @Override
    InflaterInputStream newCodecInputStream(final InputStream in) {
        return new InflaterInputStream(in);
    }
}
