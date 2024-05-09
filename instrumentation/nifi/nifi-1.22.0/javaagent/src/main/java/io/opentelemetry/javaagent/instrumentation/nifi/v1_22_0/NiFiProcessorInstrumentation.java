/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import com.google.common.base.VerifyException;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.nifi.processor.Processor;

public class NiFiProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers
        .hasSuperType(ElementMatchers.is(Processor.class));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("onTrigger"),
//            .and(ElementMatchers.takesArgument(0, ProcessContext.class))
//            .and(ElementMatchers.takesArgument(1, ProcessSession.class))
//            .and(ElementMatchers.takesArguments(2))
//            .and(ElementMatchers.not(ElementMatchers.isAbstract())),
        this.getClass().getName() + "$NiFiProcessorOnTriggerAdvice");
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessorOnTriggerAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.This Processor processor
    ) {
      throw new VerifyException("BVL");
      //Span.current().setAttribute("nifi.processor.name", processor.getClass().getName());
    }
  }
}
