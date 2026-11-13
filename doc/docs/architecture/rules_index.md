# Navigation by Project Rules & Guidelines

This index organizes the ENI Workshop quality gatekeepers, AI operational regulations, and Java coding/testing standards adapted from **Nunki** and **Quasar** (pure C++ rules excluded; Java-viable rules adapted).

---

## Quality Standards & Traceability

- **[CS-0010 Quality Standards](CS-0010.md)**
  - Mandatory requirement (`REQ-XXXXX`), feature (`FR-XXXXX`), task (`TSK-XXXXX`), bug (`BUG-XXXXX`), and fix (`FIX-XXXXX`) traceability.
  - Continuous Checkstyle, PMD, and JaCoCo build gatekeepers (`failOnViolation=true`).

## AI Agent & Submission Protocols

- **[CS-0020 AI Agent & Operational Rules](CS-0020.md)**
  - Adapted from Nunki `CS-0020` and Quasar `CS-0070`.
  - Step-by-step planning, impact/deletion analysis, full local build + test pass, and human clearance before `git commit` or `git push`.

## Java Programming & Deterministic Logic Standards

- **[CS-0030 Java Programming Standards](CS-0030.md)**
  - Adapted from Nunki `CS-0030` (DL4J/audio specifics replaced with EtherCAT StAX/ByteBuffer/OPC-UA/ZeroMQ bounds).
  - `Objects.requireNonNull`, zero null passing/returning, bounded object/thread pools, State/Strategy/Factory patterns, explicit typing (no `var`), `try-with-resources`, constructor DI, GraalVM Native Image reachability.
- **[CS-0060 Deterministic Logic Standards](CS-0060.md)**
  - Adapted from Quasar `CS-0060` (C++ MISRA rules mapped to Java).
  - Non-ignored return checks, zero variable shadowing, total `if/else if/else` & `switch` `default` branching, `sealed` hierarchies, seeded deterministic RNG in tests.

## Documentation & Testing Standards

- **[CS-0090 Formal Javadoc & Traceability](CS-0090.md)**
  - Adapted from Quasar `CS-0090` (Doxygen C++ mapped to Java Javadoc).
  - Complete `@param`, `@return`, `@throws`, `@requirement`/`@task`/`@constraint`/`@feature` traceability, 7-bit ASCII source files, and inline control-structure comments.
- **[CS-0100 Comprehensive Testing Standards](CS-0100.md)**
  - Adapted from Quasar `CS-0100` to JUnit 5, AssertJ, Mockito, Playwright, and JaCoCo.
  - Universal public method coverage, domain/boundary tests, concurrency/stress tests, deterministic seeds, resource bounds, and PlantUML test documentation.
