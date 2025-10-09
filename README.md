# Item Detail Service

API backend (Spring Boot) que expone datos de detalle de ítems (inspirado en MercadoLibre). El objetivo fue construir un servicio robusto sin base de datos real, aplicando buenas prácticas de diseño, validación y testing.

---

## Arquitectura y diseño

### Estructura general
El sistema sigue una arquitectura en capas clara:

- **Controller** → recibe y valida requests HTTP (DTOs con `@Valid`).
- **Service** → contiene la lógica de negocio (validaciones, duplicados, descuentos, rating, etc.).
- **Repository** → maneja la persistencia local en disco (`FileItemRepository`).
- **Domain** → entidades de negocio inmutables (`Item`, `Price`, `Discount`, `Rating`, etc.).
- **Mapper** → convierte entre DTOs y objetos de dominio.

Cada capa está testeada de forma independiente, siguiendo un enfoque **TDD** durante el desarrollo.

### Persistencia
- No hay DB ni caché externos.
- Los ítems se guardan en `data/items.json` y se mantiene un backup automático (`items.json.bak`).
- El repositorio usa un **lock R/W** para evitar condiciones de carrera.
- Al iniciar, si no existe el archivo, se crea automáticamente.

### Manejo de errores
La API sigue el estándar **Problem Details (RFC 9457)** para respuestas homogéneas ante errores.

Ejemplo:
```json
{
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more fields have invalid values",
  "errors": [
    { "field": "price.amount", "message": "must be >= 0", "rejectedValue": -10.00 }
  ],
  "code": "VALIDATION_ERROR"
}
```

### Validaciones y consistencia
- Bean Validation (`jakarta.validation`) en DTOs.
- Validaciones adicionales en el dominio (`Item.validate()`).
- Se evita duplicar ítems del mismo vendedor con el mismo título **normalizado** (`titleNormalized`).
- `titleNormalized` se **deriva** internamente de `title` (no se recibe por API).

### Testing
- **Unit tests**: domain, service y repository.
- **Integration tests**: controllers y exception handler con MockMvc.
- Todos los casos importantes (validación, duplicados, errores de formato) están cubiertos.

### Logging
- Uso de `LoggerFactory` con formato simplificado.
- Colores por nivel (`INFO`, `WARN`, `ERROR`).
- Mensajes uniformes y orientados a trazabilidad.

### Decisiones clave
- **Evitar complejidad innecesaria:** sin DB, sin frameworks extra (solo Spring Boot core), y sin autenticación.
- **Código expresivo y testeado:** prioridad a la legibilidad y robustez sobre performance.

---

## Uso de GenAI / tooling
El proyecto se desarrolló con asistencia de herramientas de IA (ChatGpt + Claude) para refactorización, generación de tests y documentación. Las decisiones técnicas y de diseño fueron validadas mediante pruebas automatizadas.
