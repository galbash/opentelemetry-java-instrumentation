package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessSession;

public class ProcessSpanTracker {
  private static final VirtualField<ProcessSession, ConcurrentHashMap<String, ProcessSpanDetails>> virtualField =
      VirtualField.find(ProcessSession.class, ConcurrentHashMap.class);

  private ProcessSpanTracker() {}

  public static void set(ProcessSession session, FlowFile file, Span span, Scope scope) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = virtualField.get(session);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      virtualField.set(session, map);
    }
    String id = file.getAttribute(CoreAttributes.UUID.key());
    map.put(id, new ProcessSpanDetails(span, scope));
  }

  public static void close(ProcessSession session) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = virtualField.get(session);
    if (map == null) {
      return;
    }

    map.values().forEach(details -> {
      if (details != null) {
        details.span.addEvent("Closing Span");
        details.scope.close();
        details.span.end();
      }
    });
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
