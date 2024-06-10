package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.nifi.v1_22_0.ProcessSpanDetails;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Processor;


public class ProcessSpanTracker {
  private static final VirtualField<ProcessSession, ConcurrentHashMap<String, ProcessSpanDetails>> virtualField =
      VirtualField.find(ProcessSession.class, ConcurrentHashMap.class);

  private ProcessSpanTracker() {}

  public static void updateProcessorAttributes(
      ProcessSession session,
      Processor processor,
      ProcessContext processContext
  ) {
    Span currentSpan = Java8BytecodeBridge.currentSpan();
    if (session.getClass().getSimpleName().equals("HighThroughputSession")) {
      // for HighThroughputSession we need to extract the delegate standard session
      try {
        Field delegateField = session.getClass().getDeclaredField("session");
        delegateField.setAccessible(true);
        session = (ProcessSession) delegateField.get(session);

      } catch (NoSuchFieldException | IllegalAccessException e) {
        currentSpan.recordException(e);
      }
    }
    ConcurrentHashMap<String, ProcessSpanDetails> map = getOrCreateMap(session, "upa");
    currentSpan.addEvent("In update ProcessorAttributes",
        Attributes.of(
            AttributeKey.longKey("map_size"), (long) map.size()
        ));
    for (ProcessSpanDetails details : map.values()) {
      Span span = details.span;
      currentSpan.addEvent("Updating specific span",
          Attributes.of(
              AttributeKey.stringKey("spanId"), span.getSpanContext().getSpanId(),
              AttributeKey.stringKey("new name"),
              processor.getClass().getSimpleName() + ":" + processContext.getName()
          ));
      span.updateName(processor.getClass().getSimpleName() + ":" + processContext.getName());
      span.setAttribute("nifi.component.name", processContext.getName());
      span.setAttribute("nifi.component.type", processor.getClass().getName());
      span.setAttribute("nifi.component.id", processor.getIdentifier());

    }
  }

  public static void set(ProcessSession session, FlowFile file, Span span, Scope scope) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = getOrCreateMap(session, "set");
    String id = file.getAttribute(CoreAttributes.UUID.key());
    map.put(id, new ProcessSpanDetails(span, scope));
  }

  private static ConcurrentHashMap<String, ProcessSpanDetails> getOrCreateMap(
      ProcessSession session,
      String more) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = virtualField.get(session);
    Span currentSpan = Java8BytecodeBridge.currentSpan();
    currentSpan.addEvent("Getting map " + more,
        Attributes.of(
            AttributeKey.stringKey("session_tostring"), session.toString(),
            AttributeKey.stringKey("session_simpleclass"), session.getClass().getSimpleName(),
            AttributeKey.longKey("session_hashcode"), (long) session.hashCode()
        ));
    if (map == null) {
      map = new ConcurrentHashMap<>();
      currentSpan.addEvent("Creating new map " + more,
          Attributes.of(
              AttributeKey.stringKey("map_tostring"), map.toString(),
              AttributeKey.longKey("map_hashcode"), (long) map.hashCode(),
              AttributeKey.stringKey("session_tostring"), session.toString(),
              AttributeKey.longKey("session_hashcode"), (long) session.hashCode()
          ));
      virtualField.set(session, map);
    }
    return map;
  }

  public static ProcessSpanDetails get(ProcessSession session, FlowFile file) {
    ConcurrentHashMap<String, ProcessSpanDetails> map = getOrCreateMap(session, "get");
    String id = file.getAttribute(CoreAttributes.UUID.key());
    return map.get(id);

  }

  public static void close(ProcessSession session) {
    Span currentSpan = Java8BytecodeBridge.currentSpan();
    ConcurrentHashMap<String, ProcessSpanDetails> map = virtualField.get(session);
    if (map == null) {
      currentSpan.addEvent("Close process span tracker called",
          Attributes.of(
              AttributeKey.booleanKey("null"), true
          ));
      return;
    }
    currentSpan.addEvent("Close process span tracker called",
        Attributes.of(
            AttributeKey.longKey("map_size"), (long) map.size()
        ));

    map.values().forEach(details -> {
      if (details != null) {
        details.span.addEvent("Closing Span");
        details.scope.close();
        details.span.end();
      }
    });
    map.clear();
  }

  public static void migrate(ProcessSession oldSession, ProcessSession newSession,
      Collection<FlowFile> flowFiles) {
    ConcurrentHashMap<String, ProcessSpanDetails> oldMap = getOrCreateMap(oldSession,
        "migrate old");
    ConcurrentHashMap<String, ProcessSpanDetails> newMap = getOrCreateMap(newSession,
        "migrate new");
    for (FlowFile file : flowFiles) {
      String id = file.getAttribute(CoreAttributes.UUID.key());
      if (oldMap.containsKey(id)) {
        newMap.put(id, oldMap.remove(id));
      }
    }
  }
}
