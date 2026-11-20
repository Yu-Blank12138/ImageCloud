# Repository instructions

- Place Spring Boot YAML configuration files in `src/main/resources`.
- Declare the active profile and shared database property placeholders in `application.yaml`. Define development values in `application-dev.yaml`.
- Connect directly to the development MySQL and Redis endpoints configured in `application-dev.yaml`.
- Add example configuration files only when explicitly requested.
- Append every new `CREATE TABLE` statement to the repository-root `src/main/resources/schema.sql` before executing it.
- Keep `src/main/resources/schema.sql` as the final `CREATE TABLE` definitions. Fold later table changes into the corresponding definitions instead of retaining `ALTER TABLE` history.
- Modify code only when the user explicitly asks for code changes. When the user asks a question, answer it without editing code.
- Prefer implementing new features through existing extension points or new files. Keep changes to existing code limited to what the feature requires.
- Respect task and module ownership. Modify only code assigned to the current agent, and preserve changes owned by other contributors.
- Backend agents must leave frontend implementation changes to the frontend owner. When an API change requires frontend adaptation, describe the updated contract and coordinate through the designated owner or coordinator, using messaging tools only when the user has authorized that communication.
- Cross-module dependencies do not expand an agent's editing scope. Changes outside the assigned scope require an explicit assignment from the user.

## Architecture and dependencies

- Use [design.md](design.md) as the source of truth for the technology stack, module responsibilities, and data flows. Update it when an approved change alters those decisions.
- Group backend packages by technical layer. Place controllers in `com.blank.imagecloud.controller` and database entities in `com.blank.imagecloud.models.entity`. Preserve the existing `models` hierarchy.
- Declare business service interfaces in `com.blank.imagecloud.service`. Place their implementation classes in `com.blank.imagecloud.service.impl` and inject services by their interfaces.
- Place API request DTOs in `com.blank.imagecloud.models.dto`, reuse existing model subpackages for response/result classes, and keep shared response wrappers in `com.blank.imagecloud.response`.
- Place response/result VO classes, including image upload results, in `com.blank.imagecloud.models.vo` and its business subpackages. Reserve `com.blank.imagecloud.models.entity` for database table entities.
- Reuse the user's existing package layout and preserve manual file moves. Do not create top-level `entity`, `dto`, or `vo` packages or relocate existing classes unless the user explicitly requests that package change.
- Use regular classes for Java DTOs and other data carrier types.
- Keep reusable JWT operations in the static utility class `com.blank.imagecloud.utils.JwtUtil`. Pass the signer and configuration to its methods.
- Use the owning module's service interface for cross-module calls and keep its persistence and cloud SDK details internal.
- Prefer reusing existing methods that meet the requirement. Add a new method only when existing methods cannot reasonably fulfill the requested behavior.
- Prefer Hutool BeanUtil.copyProperties for compatible bean assignments. Use CopyOptions.ignoreNullValue() for partial updates so omitted and null fields preserve existing values, and limit copies to the fields allowed by the operation. Handle type and timezone conversions explicitly. Set server-owned fields separately.
- Reuse the JDK, browser APIs, and existing dependencies where they meet the requirement. Add a dependency when a concrete feature needs it. Verify compatibility with the project's Java and Spring Boot versions.
- Add a comment immediately above each dependency declaration, including annotation processor dependencies. Name the library, explain its purpose in the project, and link to its official reference documentation.

## Database transactions

- Store business dates and times directly as LocalDateTime and MySQL DATETIME(3), without UTC conversions. MySQL generates created_at through DEFAULT CURRENT_TIMESTAMP(3), initializes edit_time from created_at, and maintains update_time through DEFAULT CURRENT_TIMESTAMP(3) and ON UPDATE CURRENT_TIMESTAMP(3). Exclude created_at and update_time from generated INSERT and UPDATE assignments. User and administrator edit statements explicitly set edit_time = CURRENT_TIMESTAMP(3), using the same database clock as creation and update timestamps. Logical deletion uses MyBatis-Plus deleteById and lets MySQL maintain update_time. JWT protocol timestamps and token expiry calculations retain their required instant semantics.

- Use database autocommit by default for a single INSERT, UPDATE, or DELETE statement.
- Use ordinary queries and autocommit for low-concurrency user and profile updates. Reserve explicit SELECT FOR UPDATE locks for inventory, monetary operations, and other critical consistency-sensitive writes that require them.
- Build partial update entities from the submitted non-null fields. Avoid writing a full previously loaded entity, which can overwrite unrelated concurrent changes.
- Add an explicit transaction only when the business operation requires multiple statements to succeed or roll back together, row locks to remain held across validation and writes, or a consistent snapshot across queries.
- A follow-up query after a single write does not by itself require a transaction. Define whether query failure should roll back the write based on the business requirement.
- Use ordinary queries for pagination by default. Require a shared snapshot for the count and records only when the business contract explicitly needs that consistency.
- When adding or changing a transaction, explain its concrete purpose in a concise Chinese developer-facing comment and keep its scope limited to the database work that requires it.

## API documentation

- Return `Response<T>` directly from REST endpoints that use the shared response envelope. Set HTTP headers through the servlet response when needed.
- Document all new and changed REST endpoints with OpenAPI 3 annotations for the project's Knife4j documentation. Use `@Tag` on controllers, `@Operation` on endpoint methods, and `@Schema` on request DTOs and response VOs. Use `@Parameter` and `@ApiResponse` to describe endpoint inputs and status codes where applicable.
- Keep documentation consistent with the actual request and response contract. Verify endpoint documentation in `/v3/api-docs` and use `/doc.html` to view it in Knife4j.

## Verification

- Do not create or modify test files unless the user explicitly requests tests. Verify changes with existing checks, compilation, and temporary validation scripts as appropriate.

## Comments and diagnostics

- Add necessary logs for every new or changed API. Reuse the shared API logging aspect for request method, path, operator ID, business response code, and duration. Record meaningful write results in the owning service. Log expected failures at WARN and unexpected failures with stack traces at ERROR through the global exception handler. Keep logs visible at the configured application level. Never log passwords, password hashes, JWTs, Authorization headers, full request or response bodies, or signed URLs.
- Use `ThrowUtils.throwIf` for conditional business exceptions and `ThrowUtils.throwException` when the current branch has already established failure.
- Write comments intended for agents in English.
- When adding or changing code, include concise developer-facing comments that explain its purpose, important behavior, and relevant constraints.
- Prefer Chinese for comments that explain code to developers.
- Document each new or modified Java method with multiline Javadoc (`/** ... */`). Describe its purpose in Chinese, add a descriptive `@param` for each parameter, and explain the result with `@return` for non-void methods.
- For each persisted field in a database entity, declare its MyBatis-Plus mapping explicitly and add Chinese Javadoc describing its purpose. Document special values and timestamp ownership where applicable.
- Internal log messages and exception messages may be in English.
