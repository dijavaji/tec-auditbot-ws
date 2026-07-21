---
inclusion: always # siempre

---

# Technology Stack

| Concern | Technology |
|---------|------------|
| Language | Java 17 |
| Framework | Spring Boot 4.x |
| Web | Spring MVC (`spring-boot-starter-webmvc`) |
| AI | Spring AI 2.0 with Ollama (`spring-ai-starter-model-ollama`) |
| Boilerplate | Lombok (annotation processor configured in Maven) |
| Build | Maven (wrapper: `./mvnw`) |
| Testing | JUnit 5, `spring-boot-starter-webmvc-test` |

## Common Commands

| Task | Command |
|------|---------|
| Clean build | `./mvnw clean install` |
| Run application | `./mvnw spring-boot:run` |
| Run all tests | `./mvnw test` |
| Run single test | `./mvnw test -Dtest=ClassName` |

## Conventions

- Use Lombok annotations (`@Getter`, `@Setter`, `@Builder`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`) to reduce boilerplate.
- Lombok is excluded from the final Spring Boot jar via `spring-boot-maven-plugin` configuration.
- Constructor injection only — never `@Autowired` on fields.
- Prefer Java Records for immutable DTOs.
- Use Jakarta Bean Validation annotations on request DTOs.
- `@Transactional` belongs in the service layer only.
