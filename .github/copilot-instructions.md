# Copilot Instructions for Mimir

## Java Toolchain Requirement

- Use OpenJDK 17 for all build, test, and Gradle tasks in this repository.
- Do not use JDK 21 or other versions unless the user explicitly asks for it.

## macOS Setup (Homebrew)

- Install JDK 17 if missing: `brew install openjdk@17`
- Prefer this JAVA_HOME in terminals and scripts:
  - `export JAVA_HOME="$(/usr/libexec/java_home -v 17)"`
  - `export PATH="$JAVA_HOME/bin:$PATH"`

## Command Execution Rule

Before running Gradle commands, verify Java 17 is active:

- `java -version`

If Java 17 is not active, set `JAVA_HOME` to OpenJDK 17 first, then continue.
