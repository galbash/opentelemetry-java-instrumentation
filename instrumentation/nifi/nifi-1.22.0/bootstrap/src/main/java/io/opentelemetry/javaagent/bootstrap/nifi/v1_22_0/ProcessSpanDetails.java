package io.opentelemetry.javaagent.bootstrap.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

public class ProcessSpanDetails {
  public Span span;
  public Scope scope;

  public ProcessSpanDetails(Span span, Scope scope) {
    this.span = span;
    this.scope = scope;
  }

}
