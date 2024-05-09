package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

public final class NifiProcessSessionSingletons {
  static Tracer tracer = GlobalOpenTelemetry.getTracer("nifi");

  private NifiProcessSessionSingletons() {}

  public static void startProcessSessionSpan(ProcessSession session, FlowFile flowFile) {
    Context extractedContext = GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(),
            FlowFileAttributesTextMapGetter.INSTANCE);
    Span span = tracer.spanBuilder("Handle Flow File")
        .setParent(extractedContext)
        .startSpan();
    for (Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
      span.setAttribute(entry.getKey(), entry.getValue());
    }
    span.addEvent("Starting file handle " + flowFile.getId());
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, span, scope);
  }

  public static void startProcessSessionSpan(ProcessSession session, List<FlowFile> flowFiles) {
    if (flowFiles.size() == 1) {
      startProcessSessionSpan(session, flowFiles.get(0));
      Span.current().addEvent("get multiple, event size is 1");
      return;
    }

    SpanBuilder spanBuilder = tracer.spanBuilder("Handle Flow Files");
    List<Context> parentContexts = flowFiles.stream().map(flowFile -> {
      return GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(),
              FlowFileAttributesTextMapGetter.INSTANCE);
    }).collect(Collectors.toList());

    for (Context context : parentContexts) {
      spanBuilder.addLink(Span.fromContext(context).getSpanContext());
    }

    Span span = spanBuilder.startSpan();
    span.addEvent("get multiple");
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, span, scope);
  }

  public static FlowFile injectContextToFlowFile(FlowFile flowFile, ProcessSession processSession,
      Span currentSpan) {
    currentSpan.addEvent(
        "Injecting attributes" + Java8BytecodeBridge.currentContext().toString());
    currentSpan.addEvent("Injecting to flow file " + flowFile.getId());
    Map<String, String> carrier = new HashMap<>();
    TextMapSetter<Map<String, String>> setter = FlowFileAttributesTextMapSetter.INSTANCE;
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(Java8BytecodeBridge.currentContext(), carrier, setter);
    return processSession.putAllAttributes(flowFile, carrier);
  }

  public static List<FlowFile> injectContextToFlowFiles(Collection<FlowFile> flowFiles,
      ProcessSession processSession, Span currentSpan) {
    currentSpan.addEvent("transfer multiple");
    return flowFiles
        .stream()
        .map(flowFile -> injectContextToFlowFile(flowFile,
            processSession, currentSpan))
        .collect(Collectors.toList());
  }
}
