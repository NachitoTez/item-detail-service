# Ejecución del proyecto

Guía rápida para ejecutar y probar el servicio **Item Detail Service**.

---

## Requisitos
- Java 21
- Maven 3.9+ (o el wrapper incluido `./mvnw`)
- (Opcional) Docker si se desea empaquetar

---

## Correr localmente

### 1️⃣ Ejecutar tests
```bash
./mvnw clean test
```

### 2️⃣ Iniciar el servidor (puerto 8080 por defecto)
```bash
./mvnw spring-boot:run
```

### 3️⃣ (Opcional) Cambiar carpeta de datos
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--items.data-dir=./data"
```

---

## Ejemplos rápidos (curl)

### Crear ítem
```bash
curl -sX POST localhost:8080/items -H "Content-Type: application/json" -d '{
  "title":"Mouse Gamer",
  "description":"Ergonómico",
  "price":{"currency":"ARS","amount":9999.90},
  "stock":10,
  "sellerId":"SELLER-1",
  "condition":"NEW",
  "freeShipping":true
}'
```

### Obtener detalle
```bash
curl -s localhost:8080/items/{id}
```

### Aplicar descuento
```bash
curl -sX POST localhost:8080/items/{id}/discount -H "Content-Type: application/json" -d '{
  "type":"PERCENT",
  "value":15,
  "label":"Hot Sale"
}'
```

---

## Endpoints principales
- `GET /items/{id}` → detalle de ítem.
- `GET /items?page&size` → listado paginado.
- `POST /items` → crea ítem.
- `PUT /items/{id}` → actualiza campos básicos.
- `DELETE /items/{id}` → elimina.
- `POST /items/{id}/rating?stars=1..5` → califica.
- `POST /items/{id}/discount` → aplica descuento.
- `DELETE /items/{id}/discount` → limpia descuento.
