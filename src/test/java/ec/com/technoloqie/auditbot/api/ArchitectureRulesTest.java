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
}
