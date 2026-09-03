# tec-auditbot-ws

`tec-auditbot-ws` es un microservicio de IA diseñado para auditar automáticamente las conversaciones de chatbots empresariales. Su objetivo es detectar fallas de conocimiento, frustración del usuario y oportunidades de venta perdidas, convirtiendo estos hallazgos en recomendaciones accionables.

## 🚀 Características Principales

1.  **Detector de Gaps de Conocimiento**: Identifica preguntas que el bot no supo responder para priorizar contenido faltante.
2.  **Analizador de Sentimiento y Frustración**: Mide la evolución emocional de cada conversación y marca puntos de fricción.
3.  **Detector de Oportunidades de Venta Perdidas**: Identifica intención de compra no concretada para recuperación de leads.
4.  **Motor de Recomendaciones**: Genera automáticamente sugerencias de FAQs y flujos alternativos.
5.  **Dashboard de Auditoría**: Expone resultados visuales para los equipos de negocio (React).

## 🛠️ Tech Stack

*   **Lenguaje**: Java 17
*   **Framework**: Spring Boot
*   **IA**: Spring AI con modelos open-source (Ollama)
*   **Mensajería**: RabbitMQ (Integración asíncrona)
*   **Seguridad**: Spring Security + JWT
*   **Pruebas**: Testcontainers, ArchUnit

## 🏗️ Principios Arquitectónicos

*   **Auditoría Pasiva**: Observa y analiza sin interferir en el flujo en vivo del chatbot.
*   **Desacoplamiento por Eventos**: Integración asíncrona para no afectar el rendimiento del sistema productor.
*   **IA Autoalojada**: Privilegia el uso de Ollama para mantener control de costos y privacidad de datos.
*   **Extensibilidad**: Diseño basado en analizadores intercambiables.

## 📋 Requisitos Previos

*   Java 17 o superior.
*   Maven 3.8+.
*   [Ollama](https://ollama.ai/) instalado y en ejecución.
*   RabbitMQ en ejecución (o vía Docker para pruebas).

## 🚀 Ejecución Local

1.  **Clonar el repositorio**:
    ```bash
    git clone https://github.com/technoloqie/tec-auditbot-ws.git
    cd tec-auditbot-ws
    ```

2.  **Configurar dependencias**: Asegúrate de tener Ollama configurado con los modelos necesarios.

3.  **Compilar y ejecutar**:
    ```bash
    ./mvnw spring-boot:run
    ```

4.  **Ejecutar pruebas**:
    ```bash
    ./mvnw test
    ```

## 📂 Estructura del Proyecto

```text
src/main/java/ec/com/technoloqie/auditbot/api/
├── analyzer/        # Componentes especializados de análisis
├── commons/         # Utilidades y excepciones comunes
├── config/          # Configuración de Spring y beans
├── controller/      # Endpoints REST
├── domain/          # Modelos de dominio
├── dto/             # Objetos de transferencia de datos
├── integration/     # Adaptadores externos (RabbitMQ, Ollama)
├── publisher/       # Publicadores de eventos
└── service/         # Lógica de negocio y orquestación
```

## 📄 Documentación Adicional

Para más detalles, consulta la carpeta `docs/`:
*   [Product Overview](docs/product.md)
*   [Data Model](docs/data-model.md)
*   [Technical Architecture](docs/tech.md)
*   [Standards](docs/base-standards.md)

---
© 2024 Technoloqie
