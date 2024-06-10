/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import java.util.Collection;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;

/**
 * Open a span on get / create with single file for each of the files
 * Close the span on commit
 * Close span before create with list (merge)
 * * Maybe we want to put them aside, deal with the batch and close only at the end at the merge
 * on create with list create new trace and add links
 * Inject *right* context on transfer (in case of batch find correct one)
 */
public class NiFiProcessSessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.hasSuperType(
        namedOneOf("org.apache.nifi.controller.repository.StandardProcessSession"));
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        namedOneOf("get").and(takesNoArguments()).and(returns(FlowFile.class)),
        this.getClass().getName() + "$NiFiProcessGetAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("get").and(takesArguments(2)).and(returns(List.class)),
        this.getClass().getName() + "$NiFiProcessGetListAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("create").and(takesNoArguments().or(takesArguments(FlowFile.class))),
        this.getClass().getName() + "$NiFiProcessGetAdvice");

    typeTransformer.applyAdviceToMethod(namedOneOf("create").and(takesArguments(Collection.class)),
        this.getClass().getName() + "$NiFiProcessCreateMergeAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(takesArgument(0, FlowFile.class)),
        this.getClass().getName() + "$NiFiProcessTransferAdvice");

    typeTransformer.applyAdviceToMethod(
        namedOneOf("transfer").and(takesArguments(2)).and(takesArgument(0, Collection.class)),
        this.getClass().getName() + "$NiFiProcessTransferListAdvice");

    typeTransformer.applyAdviceToMethod(namedOneOf("checkpoint").and(takesArguments(boolean.class)),
        this.getClass().getName() + "$NiFiProcessCheckpointAdvice");
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessGetAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return FlowFile flowFile
    ) {
      if (flowFile != null) {
        ProcessSessionSingletons.startProcessSessionSpan(session, flowFile);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessGetListAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return List<FlowFile> flowFiles
    ) {
      if (flowFiles != null) {
        ProcessSessionSingletons.startProcessSessionSpan(session, flowFiles);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCreateMergeAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(
        @Advice.This ProcessSession session,
        @Advice.Return FlowFile createFlowFile,
        @Advice.Argument(0) Collection<FlowFile> inputFlowFiles
    ) {
      ProcessSpanTracker.close(session);
      ProcessSessionSingletons.startProcessSessionSpan(session, inputFlowFiles);
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
      Span currentSpan = Java8BytecodeBridge.currentSpan();
      flowFile = ProcessSessionSingletons.injectContextToFlowFile(
          flowFile,
          processSession,
          currentSpan
      );
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessTransferListAdvice {

    //@Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.OnMethodEnter()
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Collection<FlowFile> flowFiles,
        @Advice.This ProcessSession processSession
    ) {
      Span currentSpan = Java8BytecodeBridge.currentSpan();
      flowFiles = ProcessSessionSingletons.injectContextToFlowFiles(
          flowFiles,
          processSession,
          currentSpan
      );
    }
  }

  @SuppressWarnings("unused")
  public static class NiFiProcessCheckpointAdvice {

    //@Advice.OnMethodExit(suppress = Throwable.class)
    @Advice.OnMethodExit()
    public static void onExit(@Advice.This ProcessSession session) {
      ProcessSpanTracker.close(session);
    }
  }
}
