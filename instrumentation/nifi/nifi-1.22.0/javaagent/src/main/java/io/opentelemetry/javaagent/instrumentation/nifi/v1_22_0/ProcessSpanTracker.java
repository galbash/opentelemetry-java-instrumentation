package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.apache.nifi.processor.ProcessSession;

public class ProcessSpanTracker {
  private static final VirtualField<ProcessSession, ProcessSpanDetails> virtualField =
      VirtualField.find(ProcessSession.class, ProcessSpanDetails.class);

  private ProcessSpanTracker() {}

  public static void set(ProcessSession session, Span span, Scope scope) {
    virtualField.set(session, new ProcessSpanDetails(span, scope));
  }

  public static void close(ProcessSession session) {
    ProcessSpanDetails details = virtualField.get(session);
    if (details != null) {
      details.span.addEvent("Closing Span");
      details.scope.close();
      details.span.end();
    }
  }

  public static class ProcessSpanDetails {
    public Span span;
    public Scope scope;

    public ProcessSpanDetails(Span span, Scope scope) {
      this.span = span;
      this.scope = scope;
    }

  }
}
