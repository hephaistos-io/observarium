---
name: lint
description: Run Spotless formatting and PMD static analysis checks. Auto-fixes formatting issues.
allowed-tools: Bash(./gradlew *)
---

# Lint

Apply Spotless formatting and run PMD dead code analysis.

## Process

1. Run !`./gradlew spotlessApply` to auto-fix formatting
2. Run `./gradlew pmdMain` to check for dead code and static analysis violations
3. Report any PMD violations found
