package io.servicetalk.client.api;

public final class MetadataKeys {

    /**
     * Metadata that describes the relative weight of an endpoint.
     */
    public static final Metadata.Key<Double> WEIGHT = new Metadata.Key<>(Double.class, "endpoint.weight", 1.0);
    private MetadataKeys() {
        // no instances.
    }
}
