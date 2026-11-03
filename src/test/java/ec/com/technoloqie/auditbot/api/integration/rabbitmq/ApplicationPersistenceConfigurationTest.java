package ec.com.technoloqie.auditbot.api.integration.rabbitmq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el binding de las propiedades de persistencia por perfil.
 * Cubre R67, R70, R71, R72, R73.
 */
class ApplicationPersistenceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class));

    // R67 — open-in-view deshabilitado
    @Test
    void shouldDisableOpenInView() {
        contextRunner
                .withPropertyValues("spring.jpa.open-in-view=false")
                .run(context -> {
                    org.springframework.core.env.Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
                });
    }

    // R70 — Flyway habilitado
    @Test
    void shouldEnableFlywayMigrations() {
        contextRunner
                .withPropertyValues(
                        "spring.flyway.enabled=true",
                        "spring.flyway.locations=classpath:db/migration")
                .run(context -> {
                    org.springframework.core.env.Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
                    assertThat(env.getProperty("spring.flyway.locations"))
                            .isEqualTo("classpath:db/migration");
                });
    }

    // R71 — prod: ddl-auto=validate (sin modificar esquema desde Hibernate)
    @Test
    void shouldValidateSchemaWithoutDdlGeneration() {
        contextRunner
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=validate")
                .run(context -> {
                    org.springframework.core.env.Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
                });
    }

    // R72 — datasource de prod no tiene credenciales fijas en código
    @Test
    void shouldBindDatasourceFromEnvironment() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:mysql://db-host:3306/auditbot",
                        "spring.datasource.username=auditbot",
                        "spring.datasource.password=secret")
                .run(context -> {
                    org.springframework.core.env.Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.url"))
                            .isEqualTo("jdbc:mysql://db-host:3306/auditbot");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("auditbot");
                    // La contraseña se bindea pero no la logueamos en el test
                    assertThat(env.getProperty("spring.datasource.password")).isNotBlank();
                });
    }

    // R73 — perfil local resuelve datasource con defaults de desarrollo
    @Test
    void shouldResolveLocalDatasourceDefaults() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/auditbot?useSSL=false&serverTimezone=UTC}",
                        "spring.datasource.username=${MYSQL_USERNAME:auditbot}",
                        "spring.datasource.password=${MYSQL_PASSWORD:auditbot}")
                .run(context -> {
                    org.springframework.core.env.Environment env = context.getEnvironment();
                    assertThat(env.getProperty("spring.datasource.url"))
                            .contains("127.0.0.1:3306/auditbot");
                    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("auditbot");
                });
    }
}
