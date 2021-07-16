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
package io.servicetalk.examples.grpc.routeguide;

import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.Point;

import java.io.IOException;
import java.net.URL;
import javax.annotation.Nullable;

public interface FeaturesFinder extends Iterable<Feature> {
    @Nullable
    Feature findFeature(Point point);

    static FeaturesFinder fromJson(URL jsonDatabase) throws IOException {
        return DefaultFeaturesFinder.fromJson(jsonDatabase);
    }

    static FeaturesFinder randomFeatures() {
        return DefaultFeaturesFinder.randomFeatures();
    }
}
