# Repository instructions

- Place Spring Boot YAML configuration files in `src/main/resources`.
- Declare the active profile and shared database property placeholders in `application.yaml`. Define development values in `application-dev.yaml`.
- Add example configuration files only when explicitly requested.
- Modify code only when the user explicitly asks for code changes. When the user asks a question, answer it without editing code.

## Architecture and dependencies

- Use [design.md](design.md) as the source of truth for the technology stack, module responsibilities, and data flows. Update it when an approved change alters those decisions.
- Group backend packages by technical layer, then by business capability within each layer, such as `controller.auth` and `service.auth`. Use the owning module's service interface for cross-module calls and keep its persistence and cloud SDK details internal.
- Reuse the JDK, browser APIs, and existing dependencies where they meet the requirement. Add a dependency when a concrete feature needs it. Verify compatibility with the project's Java and Spring Boot versions.
- Add a comment immediately above each dependency declaration, including annotation processor dependencies. Name the library, explain its purpose in the project, and link to its official reference documentation.

## API documentation

- Document all new and changed REST endpoints with OpenAPI 3 annotations for the project's Knife4j documentation. Use `@Tag` on controllers, `@Operation` on endpoint methods, and `@Schema` on request and response DTOs. Use `@Parameter` and `@ApiResponse` to describe endpoint inputs and status codes where applicable.
- Keep documentation consistent with the actual request and response contract. Verify endpoint documentation in `/v3/api-docs` and use `/doc.html` to view it in Knife4j.

## Comments and diagnostics

- Write comments intended for agents in English.
- Prefer Chinese for comments that explain code to developers.
- Internal log messages and exception messages may be in English.
