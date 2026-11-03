package ec.com.technoloqie.auditbot.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifica los límites arquitectónicos de {@code US-01}: ausencia de endpoint
 * HTTP para solicitar auditoría, encapsulamiento de tipos RabbitMQ dentro del
 * adaptador y separación entre publicación y procesamiento de auditoría.
 * Cubre R25, R26, R27.
 */
class ArchitectureRulesTest {

    private static final String BASE_PACKAGE = "ec.com.technoloqie.auditbot.api";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    @Test
    void shouldNotExposeAuditRequestHttpEndpoint() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".controller..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "ec.com.technoloqie.auditbot.api.dto.ConversationAuditRequestDto")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "ec.com.technoloqie.auditbot.api.service.IConversationAuditPublisher");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepRabbitMqTypesInsideAdapterBoundary() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        BASE_PACKAGE + ".integration.rabbitmq..",
                        BASE_PACKAGE + ".config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.amqp..",
                        "com.rabbitmq..");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepPublicationIndependentFromAuditProcessing() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".integration.rabbitmq..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".analyzer..",
                        BASE_PACKAGE + ".repository..");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepDtoServiceAndValidatorFreeOfSpringAmqpAndRabbitClient() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(BASE_PACKAGE + ".dto..", BASE_PACKAGE + ".service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.amqp..",
                        "com.rabbitmq..");

        rule.check(importedClasses);
    }

    // -------------------------------------------------------------------------
    // US-02 — reglas de frontera de consumo
    // -------------------------------------------------------------------------

    @Test
    void shouldKeepJpaTypesInsideModelAndRepository() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages(
                        BASE_PACKAGE + ".model..",
                        BASE_PACKAGE + ".repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..",
                        "org.springframework.data.jpa..");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepListenerFreeOfBusinessLogic() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".integration.rabbitmq..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".repository..");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepConsumptionIndependentFromAnalyzers() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        BASE_PACKAGE + ".integration.rabbitmq..",
                        BASE_PACKAGE + ".service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".analyzer..");

        rule.check(importedClasses);
    }

    @Test
    void shouldNotExposeAuditIntakeHttpEndpoint() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + ".controller..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "ec.com.technoloqie.auditbot.api.service.IConversationAuditIntakeService");

        rule.check(importedClasses);
    }

    @Test
    void shouldKeepTransactionalInsideServiceLayer() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        BASE_PACKAGE + ".integration.rabbitmq..",
                        BASE_PACKAGE + ".repository..")
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class);

        rule.check(importedClasses);
    }

    @Test
    void shouldNotUseConsoleLoggingInConsumptionFlow() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        BASE_PACKAGE + ".integration.rabbitmq..",
                        BASE_PACKAGE + ".service..",
                        BASE_PACKAGE + ".mapper..")
                .should().callMethod(System.class, "out")
                .orShould().callMethod(System.class, "err");

        rule.check(importedClasses);
    }

    @Test
    void shouldReuseContractValidatorInConsumption() {
        // Verifica que el paquete service.impl accede al validador del contrato
        // (no reimplementa las reglas). Regla estructural: IntakeServiceImpl
        // debe depender de ConversationAuditRequestValidator.
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().haveSimpleName("ConversationAuditIntakeServiceImpl")
                .should().dependOnClassesThat().haveSimpleName("ConversationAuditRequestValidator");

        rule.check(importedClasses);
    }
}
