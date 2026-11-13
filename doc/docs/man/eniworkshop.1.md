# eniworkshop(1)

## NAME
eniworkshop - EtherCAT Network Information (ENI) analysis, catalog, bus design, and Quasar testing workbench

## SYNOPSIS
**eniworkshop** [*OPTIONS*] [*COMMAND* [*ARGS*...]]

## DESCRIPTION
**eniworkshop** runs either as an interactive Spring Boot Web + STOMP + OpenAPI server (default when invoked without `--cli` subcommands) or as a non-web Picocli command-line tool (`--cli`).

## CLI COMMANDS
`--cli inspect <file.xml|file.xml.gz>`
: Parse and summarize an ETG.2100 ENI file (master, slaves, HotConnect groups, cyclic commands, process image).

`--cli validate <file.xml|file.xml.gz>`
: Execute ETG.1000 / ETG.2100 management rule validation and print diagnostics.

`--cli catalog extract <file.xml|file.xml.gz>`
: Extract slave and extension module profiles into JSON or MongoDB catalog.

`--cli aggregate <base.xml> <addon.xml> --out <merged.xml>`
: Aggregate two ENI topologies with automatic addressing and ProcessImage recalculation.

`--cli export-csv <file.xml> --out <slaves.csv>`
: Export slave topology or process image variables to CSV.

`--cli compare <left.xml> <right.xml> [--scope eni|topology|slave|hot-connect] [--left-key K] [--right-key K] [--json]`
: Compare two ENI files, bus topologies, individual slaves, or Hot Connect groups using outer-join, synchronized tree, and linear bus representations.

`--cli project [list|create-feature|create-task|set-status|log|audit] [--work-ref TSK-XXXXX] [--json]`
: Manage Features (`FR-*`), Tasks (`TSK-*`), CLI activity logs, and run `CS-0010` Java traceability audits.

## CONTAINER & HELPER WORKFLOWS
`./helpers/run-standalone.sh [run|run-cli|build|test|test-containers|clean]`
: Standalone build/run script supporting `--with-mongo-container` (Podman MongoDB 7.0) and container ITs.

`./helpers/run-podman.sh [build|up|down|logs|status|cli|clean]`
: Manage full Podman OCI stack (`Containerfile` + `helpers/podman-compose.yml` + optional `quasar` profile).

`./helpers/run-container-tests.sh`
: Ensure rootless Podman/Docker socket activation and execute `mvn verify -Pcontainer-tests`.

## SEE ALSO
Quasar `resoem`, `resoes`, and `OpcUaServerService` documentation.
