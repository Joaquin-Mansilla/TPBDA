#!/bin/bash
# Crea los usuarios, esquemas y bases del TPI.
# PostgreSQL ejecuta este script UNA sola vez: cuando el volumen postgres-data está vacío.
# Para volver a ejecutarlo: docker compose down -v  (borra todos los datos).
set -euo pipefail

echo ">> Creando usuarios y esquemas del TPI agro"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname agro \
     -v campos_pw="$CAMPOS_DB_PASSWORD" \
     -v tratamientos_pw="$TRATAMIENTOS_DB_PASSWORD" \
     -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'

-- 1. Un usuario por microservicio, más uno para Keycloak
CREATE ROLE campos_user       LOGIN PASSWORD :'campos_pw';
CREATE ROLE tratamientos_user LOGIN PASSWORD :'tratamientos_pw';
CREATE ROLE keycloak_user     LOGIN PASSWORD :'keycloak_pw';

-- 2. Base agro: nadie entra salvo los dos servicios
REVOKE ALL ON DATABASE agro FROM PUBLIC;
GRANT CONNECT ON DATABASE agro TO campos_user, tratamientos_user;

-- El esquema public no se usa: se le quitan los permisos a todos
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- 3. Un esquema por servicio, cuyo dueño es el usuario del servicio.
--    Cada usuario tiene control total sobre SU esquema y ningún permiso sobre el otro.
CREATE SCHEMA campos       AUTHORIZATION campos_user;
CREATE SCHEMA tratamientos AUTHORIZATION tratamientos_user;

-- Cada usuario busca tablas solo en su esquema
ALTER ROLE campos_user       IN DATABASE agro SET search_path = campos;
ALTER ROLE tratamientos_user IN DATABASE agro SET search_path = tratamientos;

-- 4. Base propia de Keycloak, en la misma instancia
CREATE DATABASE keycloak OWNER keycloak_user;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
GRANT CONNECT ON DATABASE keycloak TO keycloak_user;

SQL

echo ">> Listo: base agro (esquemas campos y tratamientos) y base keycloak"
