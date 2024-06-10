plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.nifi")
    module.set("nifi-api")
    versions.set("(,)")
  }
}

dependencies {
  implementation("org.apache.nifi:nifi-api:1.22.0")
  implementation("org.apache.nifi:nifi-repository-models:1.22.0")
  bootstrap(project(":instrumentation:nifi:nifi-1.22.0:bootstrap"))
}

tasks {
}

