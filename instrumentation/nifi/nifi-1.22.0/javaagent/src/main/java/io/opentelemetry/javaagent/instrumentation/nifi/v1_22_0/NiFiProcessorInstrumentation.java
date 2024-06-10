/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Processor;

public class NiFiProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("org.apache.nifi.processor.AbstractProcessor"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(namedOneOf("onTrigger")
            .and(takesArguments(2))
            .and(takesArgument(0, ProcessContext.class))
            .and(takesArgument(1, ProcessSession.class)),
        NiFiProcessorInstrumentation.class.getName() + "$OnTriggerAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnTriggerAdvice {
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.This Processor processor,
        @Advice.Argument(0) ProcessContext processContext,
        @Advice.Argument(1) ProcessSession processSession) {
      Span activeSpan = Span.current();

      activeSpan.updateName(processor.getClass().getSimpleName() + " " + processContext.getName());
      activeSpan.setAttribute("nifi.component.name", processContext.getName());
      activeSpan.setAttribute("nifi.component.type", processor.getClass().getName());
      activeSpan.setAttribute("nifi.component.id", processor.getIdentifier());

//      for (Map.Entry<PropertyDescriptor, String> e : processContext.getProperties().entrySet()) {
//        String name = String.join(".", e.getKey().getName().toLowerCase(Locale.ROOT).split("\\s+"));
//        activeSpan.setAttribute("nifi.processor.properties." + name, e.getValue());
//      }
    }
  }
}
