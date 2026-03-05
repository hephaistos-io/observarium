---
name: code-review
description: Review code quality, test coverage, and correctness using JaCoCo coverage verification.
allowed-tools: Bash(./gradlew *)
---

# Code Review

Run JaCoCo test coverage verification across the project, then review the results.

## Process

1. Run coverage report: `./gradlew coverageReport`
2. Report which modules pass or fail the 80% coverage threshold
3. If a module argument is provided (e.g. `/code-review :observarium-core`), run only that module: `./gradlew $ARGUMENTS:coverageReport`
