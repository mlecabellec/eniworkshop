# Apache 2.0 License Compatibility Audit

**ENI Workshop** is distributed under the **Apache License, Version 2.0** (`LICENSE`).

## Backend Maven Dependencies

| Dependency | License | Compatibility |
| :--- | :--- | :--- |
| Spring Boot / Spring Framework / Spring Data MongoDB / Spring Security / Spring Kafka / Spring WebSocket | Apache-2.0 | Native |
| Picocli (`info.picocli:picocli-spring-boot-starter`) | Apache-2.0 | Native |
| SpringDoc OpenAPI (`org.springdoc:springdoc-openapi-starter-webmvc-ui`) | Apache-2.0 | Native |
| PlantUML ASL (`net.sourceforge.plantuml:plantuml-asl`) | Apache-2.0 | Native (explicitly avoids GPL variant) |
| Eclipse Milo (`org.eclipse.milo:sdk-client`, `sdk-server`) | EPL-2.0 | Compatible as unmodified binary library |
| JeroMQ (`org.zeromq:jeromq`) | MPL-2.0 | Compatible as library dependency |
| Playwright (`com.microsoft.playwright`, test scope) | Apache-2.0 | Native |

## Frontend npm Dependencies

| Package | License | Compatibility |
| :--- | :--- | :--- |
| `svelte`, `vite`, `typescript`, `@sveltejs/vite-plugin-svelte` | MIT / Apache-2.0 | Compatible |
| `dockview-core`, `@xyflow/svelte`, `cytoscape`, `tabulator-tables`, `papaparse`, `plantuml-encoder`, `@stomp/stompjs`, `lucide-svelte`, `@viz-js/viz` | MIT | Compatible |
| `d3-scale` | ISC / BSD-3-Clause | Compatible |
