# Ejecución local de auth-api

Requisitos: JDK 21, PostgreSQL accesible y OpenSSL (solo para crear las claves si aún no existen).

1. En PowerShell, desde la raíz, ejecuta `Copy-Item .env.example .env`. Completa en `.env` las credenciales de PostgreSQL y el correo/contraseña inicial del administrador. `.env` está ignorado por Git; nunca publiques contraseñas ni claves privadas.
2. Asegúrate de que la BD indicada en `SPRING_DATASOURCE_URL` existe y que el usuario tiene los permisos necesarios para las migraciones de Auth. Si la BD ya comparte el esquema con Scoring, conserva la configuración de Flyway y el procedimiento de despliegue del README.
3. Si necesitas generar claves locales, desde la raíz ejecuta:

   ```powershell
   New-Item -ItemType Directory -Force keys | Out-Null
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-privada.pem
   openssl pkey -in keys/jwt-privada.pem -pubout -out keys/jwt-publica.pem
   ```

   La privada permanece aquí. Copia solo la pública a los repos de Scoring y Query y configura allí la ruta respectiva. El `iss` debe coincidir en las tres APIs; `aud` debe incluir `scoring-api,evaluation-query-api`.
4. Ejecuta `.\run-local.ps1` desde cualquier ubicación. El script carga `.env` solo para el proceso de Maven y ejecuta `.\mvnw.cmd spring-boot:run`; al finalizar restaura las variables previas de la terminal.

API Auth: `http://localhost:8081`. Para desarrollo, revisa el README antes de aplicar migraciones sobre una BD con datos existentes.
