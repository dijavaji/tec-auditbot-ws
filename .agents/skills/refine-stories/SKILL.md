---
name: refine-stories
description: Refina historias de usuario usando código fuente como contexto. Analiza la implementación para identificar criterios de aceptación faltantes, casos borde y oportunidades de mejora. Usar cuando se quiera mejorar user stories basándose en código existente.
---

## Instrucciones

Por favor, analice y corrija el ticket de historia de usuario: $ARGUMENTS.

Sigue estos pasos:

1. Usa el archivo `#[[file:feature_list.json]]` para obtener los detalles del ticket, ya sea el ID/número del ticket, palabras clave que lo identifiquen o indiquen su estado, como "pending".
2. Actúa como experto en el producto con conocimientos técnicos.
3. Comprende el problema descrito en el ticket.
4. Decide si la historia de usuario está completamente detallada según las mejores prácticas del producto: incluye una descripción completa de la funcionalidad, una lista exhaustiva de los campos que se deben actualizar, la estructura y las URL de los endpoints necesarios, los archivos que se deben modificar según la arquitectura y las mejores prácticas, los pasos necesarios para que la tarea se considere completa, cómo actualizar la documentación relevante o crear pruebas unitarias, y los requisitos no funcionales relacionados con la seguridad, el rendimiento, etc.
5. Si la historia de usuario carece de los detalles técnicos y específicos necesarios para que el desarrollador pueda completarla de forma totalmente autónoma, proporciona una historia mejorada que sea más clara, específica y concisa, de acuerdo con las mejores prácticas del producto descritas en el paso 4. Utiliza el contexto técnico que encontrarás en la documentación. Devuélvela en formato Markdown.
6. Guarda la historia de usuario enriquecida como un archivo Markdown en la carpeta `tmp/` de la raíz del proyecto (créala si no existe). Nombra el archivo `<id>-enriched-us.md` (por ejemplo, `US-01-enriched-us.md`). La carpeta `tmp/` ya está incluida en `.gitignore`.
7. Actualiza el campo `"status"` de la feature correspondiente en `feature_list.json` de `"pending"` a `"history_ready"`. No modifiques ningún otro campo del JSON.
8. Informa al usuario que la historia está lista para revisión:
   > "Historia refinada en `tmp/<id>-enriched-us.md`. Revísala y ejecuta el flujo de spec de Kiro (sesión Spec) cuando estés listo."

## Contexto técnico

#[[file:feature_list.json]]
#[[file:docs/backend-standards.md]]
#[[file:docs/base-standards.md]]

