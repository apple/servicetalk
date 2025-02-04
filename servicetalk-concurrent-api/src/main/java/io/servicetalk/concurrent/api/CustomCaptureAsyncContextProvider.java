package io.servicetalk.concurrent.api;

import static java.util.Objects.requireNonNull;

final class CustomCaptureAsyncContextProvider extends AbstractAsyncContextProvider {

    private final ContextCaptureProvider contextCaptureProvider;

    CustomCaptureAsyncContextProvider(ContextCaptureProvider contextCaptureProvider) {
        this.contextCaptureProvider = requireNonNull(contextCaptureProvider);
    }

    @Override
    public CapturedContext captureContext() {
        return contextCaptureProvider.captureContext();
    }

    @Override
    public CapturedContext captureContextCopy() {
        return contextCaptureProvider.captureContextCopy();
    }
}
