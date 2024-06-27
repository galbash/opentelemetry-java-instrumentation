package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.nifi.v1_22_0.ProcessSpanDetails;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

public final class ProcessSessionSingletons {
  static Tracer tracer = GlobalOpenTelemetry.getTracer("nifi");

  private ProcessSessionSingletons() {}

  public static void startProcessSessionSpan(ProcessSession session, FlowFile flowFile) {
    // if no external context was found, use root context since current context may be spam
    Context externalContext = ExternalContextTracker.pop(session,
        Java8BytecodeBridge.rootContext());
    Context extractedContext = GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(
            externalContext,
            // using root context because we want only the extracted context if exists
            flowFile.getAttributes(),
            FlowFileAttributesTextMapGetter.INSTANCE
        );
    Span span = tracer.spanBuilder("Handle Flow File")
        .setParent(extractedContext)
        .startSpan();
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, flowFile, span, scope);
  }

  public static void startProcessSessionSpan(
      ProcessSession session,
      Collection<FlowFile> flowFiles) {
    for (FlowFile flowFile : flowFiles) {
      // in case of multiple files, only the last will be "active"
      startProcessSessionSpan(session, flowFile);
    }
  }

  public static void startMergeProcessSessionSpan(
      ProcessSession session,
      Collection<FlowFile> inputFlowFiles,
      FlowFile outputFlowFile

  ) {
//    if (flowFiles.size() == 1) {
//      startProcessSessionSpan(session, new ArrayList<>(flowFiles).get(0));
//      return;
//    }

    SpanBuilder spanBuilder = tracer.spanBuilder("Handle Flow Files");
    List<Context> parentContexts = inputFlowFiles.stream()
        .map(flowFile -> GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(),
                FlowFileAttributesTextMapGetter.INSTANCE)).collect(Collectors.toList());

    for (Context context : parentContexts) {
      spanBuilder.addLink(Span.fromContext(context).getSpanContext());
    }

    Span span = spanBuilder.setNoParent().startSpan();
    Scope scope = span.makeCurrent();
    ProcessSpanTracker.set(session, outputFlowFile, span, scope);
  }

  /**
   * 1. Injects span context to flow file, creates new file
   * 2. records attributes to span
   */
  public static FlowFile handleTransferFlowFile(
      FlowFile flowFile,
      ProcessSession processSession
  ) {

    // TODO: if no details return
    ProcessSpanDetails details = ProcessSpanTracker.get(processSession,
        flowFile);
    for (Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
      details.span.setAttribute("nifi.attributes." + entry.getKey(), entry.getValue());
    }
    Map<String, String> carrier = new HashMap<>();
    TextMapSetter<Map<String, String>> setter = FlowFileAttributesTextMapSetter.INSTANCE;
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(Java8BytecodeBridge.currentContext().with(details.span), carrier, setter);
    return processSession.putAllAttributes(flowFile, carrier);
  }

  public static List<FlowFile> handleTransferFlowFiles(
      Collection<FlowFile> flowFiles,
      ProcessSession processSession
  ) {
    return flowFiles.stream()
        .map(flowFile -> handleTransferFlowFile(flowFile, processSession))
        .collect(Collectors.toList());
  }
}
