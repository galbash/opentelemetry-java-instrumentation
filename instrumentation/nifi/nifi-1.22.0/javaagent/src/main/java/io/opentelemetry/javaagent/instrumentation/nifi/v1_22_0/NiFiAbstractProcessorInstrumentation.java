/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

//import io.opentelemetry.api.GlobalOpenTelemetry;
//import io.opentelemetry.api.trace.Span;
//import io.opentelemetry.api.trace.Tracer;
//import io.opentelemetry.context.Context;
//import io.opentelemetry.context.Scope;
//import io.opentelemetry.context.propagation.TextMapGetter;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.nifi.processor.exception.ProcessException;

public class NiFiAbstractProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return ElementMatchers.named("org.apache.nifi.processor.AbstractProcessor");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("onTrigger")
            .and(ElementMatchers.isFinal()),
        this.getClass().getName() + "$NiFiProcessorOnTriggerAdvice");
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessorOnTriggerAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class, onThrowable = ProcessException.class)
    @Advice.OnMethodExit(onThrowable = ProcessException.class)
    public static void onExit() {
      //Span.current().end();
      //throw new RuntimeException("BLA BLA");
    }
  }
}
