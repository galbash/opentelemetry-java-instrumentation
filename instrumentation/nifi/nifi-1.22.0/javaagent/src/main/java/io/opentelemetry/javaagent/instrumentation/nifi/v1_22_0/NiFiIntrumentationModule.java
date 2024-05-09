/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class NiFiIntrumentationModule extends InstrumentationModule {
  public NiFiIntrumentationModule() {
    super("nifi");
  }

  @Override
  public int order() {
    return 1;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return AgentElementMatchers.hasClassesNamed(
        "org.apache.nifi.controller.repository.StandardProcessSession",
        "org.apache.nifi.processor.Processor"
    );
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new NiFiProcessSessionInstrumentation(),
        new NiFiProcessorInstrumentation()
    );
  }

/*
  @Override
//  public List<String> getAdditionalHelperClassNames() {
*/
//    return Arrays.asList(
//            "com.example.javaagent.instrumentation.NifiProcessSessionSingleton",
//            "com.example.javaagent.instrumentation.FlowFileAttributesTextMapGetter"
//    );
//  }
}
