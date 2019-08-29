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

import com.google.protobuf.util.JsonFormat;
import io.grpc.examples.routeguide.Feature;
import io.grpc.examples.routeguide.FeatureDatabase;
import io.grpc.examples.routeguide.Point;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

final class DefaultFeaturesFinder implements FeaturesFinder {
    private final List<Feature> features;

    private DefaultFeaturesFinder(final List<Feature> features) {
        this.features = features;
    }

    @Nullable
    @Override
    public Feature findFeature(final Point point) {
        for (Feature feature : features) {
            if (feature.getLocation().getLatitude() == point.getLatitude() &&
                    feature.getLocation().getLongitude() == point.getLongitude()) {
                return feature;
            }
        }
        return null;
    }

    static FeaturesFinder fromJson(URL jsonDatabase) throws IOException {
        try (InputStream input = jsonDatabase.openStream()) {
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                FeatureDatabase.Builder database = FeatureDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);
                return new DefaultFeaturesFinder(database.getFeatureList());
            }
        }
    }

    static FeaturesFinder randomFeatures() {
        List<Feature> features = new ArrayList<>();
        // Add a random feature.
        features.add(Feature.newBuilder()
                .setLocation(Point.newBuilder()
                        .setLatitude(123456)
                        .setLongitude(-123456)
                        .build())
                .setName("Dummy location.")
                .build());
        features.add(Feature.newBuilder()
                .setLocation(Point.newBuilder()
                        .setLatitude(789000)
                        .setLongitude(-789000)
                        .build())
                .setName("Dummy location.")
                .build());
        return new DefaultFeaturesFinder(features);
    }

    @Override
    public Iterator<Feature> iterator() {
        return features.iterator();
    }
}
