#!/bin/bash
# Startup script for code-semi-graph with proper JVM arguments

java --add-opens=java.base/java.lang=ALL-UNNAMED \
     --add-opens=java.base/sun.reflect.annotation=ALL-UNNAMED \
     -Dlog4j2.isThreadContextMapInheritable=true \
     -jar build/libs/code-semi-graph-1.0.0.jar "$@"

