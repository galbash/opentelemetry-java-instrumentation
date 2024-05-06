package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.validation.ValidationException;
import org.apache.nifi.controller.repository.StandardProcessSession;

public class ProcessSpanTracker {
  private static final VirtualField<StandardProcessSession, ProcessSpanDetails> virtualField =
      VirtualField.find(StandardProcessSession.class, ProcessSpanDetails.class);

  private ProcessSpanTracker() {}

  public static void set(StandardProcessSession session, Span span, Scope scope) {
    virtualField.set(session, new ProcessSpanDetails(span, scope));
  }

  public static void close(StandardProcessSession session) {
    ProcessSpanDetails details = virtualField.get(session);
    if (details != null) {
      details.scope.close();
      details.span.end();
    } else {
      throw new ValidationException("This is not intended");
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
