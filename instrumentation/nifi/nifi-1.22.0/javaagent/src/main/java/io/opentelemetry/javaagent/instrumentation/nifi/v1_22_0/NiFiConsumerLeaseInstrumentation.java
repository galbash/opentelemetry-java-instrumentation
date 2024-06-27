/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.nifi.processor.ProcessSession;

/**
 * Instrumenting Nifi Kafka processor for context injection
 */
public class NiFiConsumerLeaseInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("org.apache.nifi.processors.kafka.pubsub.ConsumerLease"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(namedOneOf("writeData")
            .and(takesArguments(3))
            .and(takesArgument(0, ProcessSession.class))
            .and(isPrivate()),
        NiFiConsumerLeaseInstrumentation.class.getName() + "$WriteDataAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteDataAdvice {
    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.Argument(0) ProcessSession session,
        @Advice.Argument(1) ConsumerRecord<byte[], byte[]> record
    ) {
      ConsumerLeaseSingletons.setContext(session, record);
    }
  }
}
