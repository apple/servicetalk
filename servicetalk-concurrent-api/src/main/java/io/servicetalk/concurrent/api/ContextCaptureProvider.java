package io.servicetalk.concurrent.api;

interface ContextCaptureProvider {

    /**
     * Save existing context in preparation for an asynchronous thread jump.
     *
     * Note that this can do more than just package up the ServiceTalk {@link AsyncContext} and could be enhanced or
     * wrapped to bundle up additional contexts such as the OpenTelemetry or grpc contexts.
     * @return the saved context state that may be restored later.
     */
    CapturedContext captureContext();

    CapturedContext captureContextCopy();
}
