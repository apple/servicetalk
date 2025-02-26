package io.servicetalk.concurrent.api;

import io.opentelemetry.context.Context;
import io.servicetalk.context.api.ContextMap;

public final class OtelAgentCapturedContextProvider implements CapturedContextProvider {

  public OtelAgentCapturedContextProvider() {
  }

  @Override
  public CapturedContext captureContext(CapturedContext underlying) {
    return new WithOtelCapturedContext(Context.current(), underlying);
  }

  static final class WithOtelCapturedContext implements CapturedContext {

    private final Context agentContext;
    private final CapturedContext stContext;

    WithOtelCapturedContext(Context agentContext, CapturedContext stContext) {
      this.agentContext = agentContext;
      this.stContext = stContext;
    }

    @Override
    public ContextMap captured() {
      return stContext.captured();
    }

    @Override
    public Scope attachContext() {
      Scope stScope = stContext.attachContext();
      io.opentelemetry.context.Scope agentScope = agentContext.makeCurrent();
      return () -> {
        agentScope.close();
        stScope.close();
      };
    }
  }
}
