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
package io.servicetalk.examples.http.service.composition.pojo;

public final class FullRecommendation {

    private Metadata entity;
    private User recommendedBy;
    private Rating overallRating;

    public FullRecommendation() {
    }

    public FullRecommendation(final Metadata entity, final User recommendedBy, final Rating overallRating) {
        this.entity = entity;
        this.recommendedBy = recommendedBy;
        this.overallRating = overallRating;
    }

    public Metadata getEntity() {
        return entity;
    }

    public void setEntity(final Metadata entity) {
        this.entity = entity;
    }

    public User getRecommendedBy() {
        return recommendedBy;
    }

    public void setRecommendedBy(final User recommendedBy) {
        this.recommendedBy = recommendedBy;
    }

    public Rating getOverallRating() {
        return overallRating;
    }

    public void setOverallRating(final Rating overallRating) {
        this.overallRating = overallRating;
    }
}
