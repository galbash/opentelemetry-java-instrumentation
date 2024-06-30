package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.nifi.v1_22_0.ProcessSpanDetails;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessSession;


public class ProcessSpanTracker {
  private static final VirtualField<ProcessSession, ConcurrentHashMap<String, ProcessSpanDetails>> processSpanDetailsMap =
      VirtualField.find(ProcessSession.class, ConcurrentHashMap.class);

  private ProcessSpanTracker() {}

  public static void set(ProcessSession session, FlowFile file, Span span, Scope scope) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = getOrCreateMap(session);
    String id = file.getAttribute(CoreAttributes.UUID.key());
    map.put(id, new ProcessSpanDetails(span, scope));
  }

  private static ConcurrentHashMap<String, ProcessSpanDetails> getOrCreateMap(
      ProcessSession session) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = processSpanDetailsMap.get(session);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      processSpanDetailsMap.set(session, map);
    }
    return map;
  }

  public static ProcessSpanDetails get(ProcessSession session, FlowFile file) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = getOrCreateMap(session);
    String id = file.getAttribute(CoreAttributes.UUID.key());
    return map.get(id);

  }

  public static void close(ProcessSession session) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = processSpanDetailsMap.get(session);
    if (map == null) {
      return;
    }

    map.values().forEach(details -> {
      if (details != null) {
        details.scope.close();
        details.span.end();
      }
    });
    map.clear();
  }

  public static void migrate(ProcessSession oldSession, ProcessSession newSession,
      Collection<FlowFile> flowFiles) {
    ConcurrentHashMap<String, ProcessSpanDetails> oldMap = getOrCreateMap(oldSession);
    ConcurrentHashMap<String, ProcessSpanDetails> newMap = getOrCreateMap(newSession);
    for (FlowFile file : flowFiles) {
      String id = file.getAttribute(CoreAttributes.UUID.key());
      if (oldMap.containsKey(id)) {
        newMap.put(id, oldMap.remove(id));
      }
    }
  }
}
