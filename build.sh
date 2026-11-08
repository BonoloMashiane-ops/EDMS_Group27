#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -d out src/EdmsPrototype.java src/EdmsGui.java test/EdmsTests.java
else
  # Some minimal JDK images have the compiler module but omit the javac launcher.
  java com.sun.tools.javac.Main -encoding UTF-8 -d out src/EdmsPrototype.java src/EdmsGui.java test/EdmsTests.java
fi
echo "Build successful (Java 17)."
