/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.nifi.controller.repository.StandardProcessSession;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

public class NiFiProcessSessionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("org.apache.nifi.controller.repository.StandardProcessSession"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("get").and(ElementMatchers.takesNoArguments()),
        this.getClass().getName() + "$NiFiProcessGetAdvice");
    typeTransformer.applyAdviceToMethod(
        namedOneOf("create"),
        this.getClass().getName() + "$NiFiProcessGetAdvice");
    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(ElementMatchers.takesArgument(0, FlowFile.class)),
        this.getClass().getName() + "$NiFiProcessTransferAdvice");
    typeTransformer.applyAdviceToMethod(
        namedOneOf("commit").and(ElementMatchers.takesArguments(2)),
        this.getClass().getName() + "$NiFiProcessCommitAdvice");
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessGetAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.Return FlowFile flowFile,
        @Advice.This StandardProcessSession session
    ) {
      TextMapGetter<Map<String, String>> getter = FlowFileAttributesTextMapGetter.INSTANCE;
      Context extractedContext = GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .extract(Java8BytecodeBridge.currentContext(), flowFile.getAttributes(), getter);
      Tracer tracer = GlobalOpenTelemetry.getTracer("nifi");
      Span span = tracer.spanBuilder("Handle Flow File")
          .setParent(extractedContext)
          .startSpan();
      for (Map.Entry<String, String> entry : flowFile.getAttributes().entrySet()) {
        span.setAttribute(entry.getKey(), entry.getValue());
      }
      Scope scope = span.makeCurrent();
      ProcessSpanTracker.set(session, span, scope);
    }

  }

  @SuppressWarnings("unused")
  public static class NiFiProcessTransferAdvice {

    //@Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) FlowFile flowFile,
        @Advice.This ProcessSession processSession
    ) {
      Map<String, String> carrier = new HashMap<>();
      TextMapSetter<Map<String, String>> setter = FlowFileAttributesTextMapSetter.INSTANCE;
      GlobalOpenTelemetry.getPropagators()
          .getTextMapPropagator()
          .inject(Java8BytecodeBridge.currentContext(), carrier, setter);
      flowFile = processSession.putAllAttributes(flowFile, carrier);
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCommitAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(@Advice.This StandardProcessSession session) {
      ProcessSpanTracker.close(session);
    }

  }

}
