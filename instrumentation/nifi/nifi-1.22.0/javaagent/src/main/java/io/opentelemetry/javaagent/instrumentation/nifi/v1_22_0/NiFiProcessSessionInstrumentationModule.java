/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nifi.v1_22_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class NiFiProcessSessionInstrumentationModule extends InstrumentationModule {
  public NiFiProcessSessionInstrumentationModule() {
    super("nifi");
  }

  @Override
  public int order() {
    return 2;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.nifi.controller.repository.StandardProcessSession")
        .or(hasClassesNamed("org.apache.nifi.processor.Processor"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    ArrayList<TypeInstrumentation> result = new ArrayList<>();
    result.add(new NiFiProcessSessionInstrumentation());
    return result;
  }
}
