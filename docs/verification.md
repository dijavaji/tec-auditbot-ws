---
description: Cómo verificar que el trabajo de un agente funciona. Incluye niveles de testing y trazabilidad de requirements.
globs:
  - "src/test/java/**/*.java"
alwaysApply: false
---

# Verificación — Cómo demostrar que el trabajo funciona

> Regla de oro: **el agente no dice "funciona", lo demuestra**.
> Toda feature termina con evidencia ejecutable, no con afirmaciones.

## Niveles de verificación

### Nivel 1 — Tests unitarios (obligatorio)

Toda clase pública en `src/main/java` que contenga lógica (services, analyzers, mappers) tiene al menos un test en `src/test/java` que:

1. Cubre el camino feliz.
2. Cubre al menos un camino de error si el método puede lanzar excepción.

**Analyzers** se testean con JUnit 5 + Mockito puro, **sin levantar contexto Spring**.

Comando:

```bash
./mvnw test
```
Para correr una sola clase:

```bash
./mvnw test -Dtest=AuditOrchestratorTest
```
Para correr un solo método:

```bash
./mvnw test -Dtest=AuditOrchestratorTest#shouldProcessConversationEndToEnd
```

### Nivel 2 — Tests de integración (obligatorio para features con persistencia o messaging)
Las features que involucran JPA o RabbitMQ se verifican con tests de integración.

Repositorios :

```java
@DataJpaTest
class AuditRepositoryTest {

    @Autowired
    private AuditRepository auditRepository;

    @Test
    void shouldFindAuditByChatId() {
        // arrange: persistir entidad con TestEntityManager o repository.save()
        // act: llamar al método del repositorio
        // assert: verificar resultado con assertThat()
    }
}

```

Endpoints REST:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnAuditWhenExists() {
        ResponseEntity<AuditResponseDto> response =
            restTemplate.getForEntity("/api/v1/audits/{chatId}", AuditResponseDto.class, "chat-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

```

RabbitMQ (con Testcontainers):

```java
@SpringBootTest
@Testcontainers
class AuditMessageConsumerIntegrationTest {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @Test
    void shouldConsumeAndProcessMessage() {
        // publicar mensaje en cola de test
        // verificar que se procesó correctamente
    }
}
```


### Nivel 3 — Smoke test manual (opcional pero recomendado)

Antes de cerrar la sesión, ejecuta el servicio y verifica un flujo end-to-end:

```bash
# Levantar la app con perfil local
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# En otra terminal, verificar health o endpoint básico
curl -s http://localhost:8080/api/v1/audits | jq .

```

### Nivel 4 — Trazabilidad de requirements (obligatorio para features con `"sdd": true`)

Cada R<n> de specs/NNN-<name>/requirements.md debe poder mapearse a al menos un test concreto en src/test/java/. El reviewer rechaza si falta cobertura.

El implementer documenta el mapa en progress/impl_<name>.md:

```markdown
## Trazabilidad
- R1 → `AuditOrchestratorTest#shouldProcessConversationEndToEnd`
- R2 → `AuditMessageConsumerTest#shouldRejectInvalidMessageToDlq`
- R3 → `AuditQueryServiceTest#shouldReturnPaginatedResults`
```

## Anti-patrones (no hacer)

- ❌ "He añadido el servicio, debería funcionar." → falta test ejecutable.
- ❌ Test que solo verifica que no lanza excepción sin comprobar resultado → usar assertThat() con valor concreto.
- ❌ Mockear el EntityManager directamente → usar @DataJpaTest con H2 embebido.
- ❌ System.out.println() para debug → usar @Slf4j de Lombok.
- ❌ e.printStackTrace() → usar log.error("mensaje descriptivo", e).
- ❌ Marcar la feature como done sin pasar ./mvnw clean verify.
- ❌ Tests que dependen de infraestructura externa real → usar @Testcontainers para RabbitMQ.
- ❌ Lógica de negocio en tests de integración → los tests de integración verifican wiring, no lógica.


## Verificación final antes de cerrar

```bash
./mvnw clean verify   # compila + ejecuta todos los tests (unitarios + integración)
```

Si `./mvnw clean verify` falla, no marques nada como done. Anota el bloqueo en 
current.md y marca estado blocked en feature_list.json.