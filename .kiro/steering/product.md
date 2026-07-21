---
inclusion: always # siempre

---

# Product Overview

tec-auditbot-ws es un microservicio de IA que audita automaticamente las conversaciones de chatbots empresariales, detectando fallas de conocimiento, frustracion del usuario y ventas perdidas, para convertir esos hallazgos en recomendaciones de mejora accionables.

1. **Detector de Gaps de Conocimiento** — identifica preguntas del usuario que el bot no supo responder, para priorizar que contenido falta en la base de conocimiento.
2. **Analizador de Sentimiento y Frustracion** — mide la evolucion emocional de cada conversacion y marca los momentos donde el usuario se frustro.
3. **Detector de Oportunidades de Venta Perdidas** — identifica intencion de compra que no se concreto, para recuperar leads no calificados.
4. **Motor de Recomendaciones** — genera automaticamente sugerencias de nuevas FAQs y flujos alternativos a partir de los hallazgos anteriores.
5. **Dashboard de Auditoria (React)** — expone los resultados de forma visual y consultable para los equipos de negocio.

## Para quien

- Equipos de soporte y CX (experiencia del cliente) de empresas que operan chatbots, que buscan saber exactamente que contenido falta y donde se frustran sus usuarios.
- Equipos de ventas y marketing, que buscan recuperar oportunidades de negocio que el chatbot dejo pasar sin visibilidad.
- Product owners de plataformas de chatbot (como Smart Chatbot), que buscan mejorar el bot de forma continua sin depender de revision manual de logs.
- Equipo tecnico/arquitectura de Smart Chatbot, como interesado directo en integrar AuditBot AI como un microservicio mas del ecosistema, sin acoplar su rendimiento al chatbot en produccion.

## Principios

- **Auditoria, no reemplazo del chatbot** — AuditBot AI nunca interviene ni modifica el comportamiento del chatbot en vivo; solo observa, analiza y recomienda.
- **Desacoplamiento por eventos** — toda integracion con sistemas productores (Smart Chatbot u otro) ocurre de forma asincrona via mensajeria, nunca por llamada sincrona que bloquee al chatbot.
- **IA open source y autoalojada** — se privilegia el uso de modelos open source via Ollama sobre servicios cloud propietarios, para mantener control de costos y de los datos analizados.
- **Simplicidad antes que completitud** — ante restricciones de tiempo y equipo, se prioriza un flujo end-to-end funcional y demostrable sobre una arquitectura distribuida completa desde el inicio.
- **Extensibilidad de analizadores** — cada tipo de analisis (gaps, sentimiento, leads, recomendaciones) se disena como una pieza intercambiable, para poder agregar nuevos analizadores sin rediseñar el sistema.

## Que NO es

- No es un chatbot ni un motor de conversacion; no responde a usuarios finales ni sustituye a Smart Chatbot.
- No es una plataforma de entrenamiento o fine-tuning de modelos de IA; consume modelos ya existentes via API de Ollama.
- No incluye, en esta version, autenticacion, autorizacion ni un API Gateway propio.
- No implementa, en esta version, deduplicacion de recomendaciones via embeddings vectoriales (queda para una version futura).
- No garantiza alta disponibilidad ni escalamiento horizontal formal; esta version prioriza demostrar el flujo funcional completo.

## Domain Concepts

- **Audit**: The core domain object representing a compliance audit process.
- **Analyzers**: Specialized components that perform discrete audit analysis tasks (gap detection, lead evaluation, recommendations, sentiment).
- **Orchestrator**: Coordinates multi-step audit workflows end-to-end.
- **Integration adapters**: Anti-corruption layer wrapping external systems (Ollama, RabbitMQ).
