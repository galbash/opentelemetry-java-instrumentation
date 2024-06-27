plugins {
  id("otel.javaagent-bootstrap")
}

dependencies {
  compileOnly("org.apache.nifi:nifi-api:1.22.0")
}
