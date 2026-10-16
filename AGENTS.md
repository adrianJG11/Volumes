# AGENTS.md — Volumes

## Contexto y alcance
- Este repositorio es un proyecto personal de aprendizaje y una biblioteca en construcción.
- Volumes usa Java 25, Maven y Spring Boot: `VolumesApplication` inicia la API; `Main` mantiene la consola. Los paquetes están bajo `com.adrian.volumes`.
- Comprueba el código, README y pom.xml al empezar: el proyecto puede evolucionar.
- La API Spring Boot persiste el catálogo en PostgreSQL mediante BookRepository y Spring JDBC; Flyway gestiona el esquema. La consola mantiene su biblioteca en memoria. Próximas fases acordadas: usuarios, frontend y después recomendador/agente en Python. No introduzcas esas dependencias por anticipado.
- MyVault es otro proyecto y está terminado; no mezcles sus datos, requisitos ni tareas.

## Colaboración y aprendizaje
- Responde en inglés salvo petición del usuario de otro idioma y explica las decisiones técnicas con ejemplos concretos.
- Para consultas o ejercicios, ofrece primero una explicación y pistas; deja que el usuario practique. Cuando solicite implementación, realiza el cambio acotado y explica cómo funciona y cómo comprobarlo.
- Trabaja en pasos pequeños y verificables. Pregunta solo por decisiones que bloqueen el paso actual.
- Reutiliza lo existente. Justifica nuevas dependencias y evita refactorizaciones ajenas al objetivo.
- No inventes progreso ni marques tareas como terminadas sin una comprobación.

## Entorno y desarrollo
- Entorno actual: Ubuntu en WSL, checkout en `/home/adrian/projects/biblioteca` (carpeta conservada tras renombrar el proyecto a Volumes). GitHub: `adrianJG11/Volumes`.
- Ejecuta Java, Maven y Git dentro de WSL. La carpeta de Windows llamada `biblioteca python+java` no es esta copia del repositorio.
- Usa las versiones y convenciones declaradas en pom.xml. No cambies el JDK ni agregues un framework sin una necesidad acordada.
- No edites `target/` ni archivos `.class`: son resultados de compilación.
- Consulta documentación oficial cuando una decisión dependa de versiones o compatibilidad.

## Verificación
- Docker debe estar activo. Inicia PostgreSQL local con `docker compose up -d --wait`; configura `.env` siguiendo README. No muestres sus credenciales.
- Los tests de catálogo, esquema y HTTP usan el perfil test con PostgreSQL desechable de Testcontainers; nunca deben usar la base de desarrollo.
- API: `mvn spring-boot:run`; empaquetado: `mvn verify` y `java -jar target/volumes-0.1.0-SNAPSHOT.jar`.
- Smoke HTTP: `python3 scripts/smoke_api.py`, tras empaquetar. Usa PostgreSQL desechable y un puerto local temporal; verifica persistencia tras reiniciar la API y recrear PostgreSQL conservando el volumen. Usa compose.yaml con un nombre de proyecto único y elimina solo los recursos de ese proyecto temporal al terminar.
- Para cambios Java, desde la raíz: `mvn compile`.
- Prueba de consola: `java -cp target/classes com.adrian.volumes.Main`.
- Hay tests JUnit Jupiter en `src/test/java` para el modelo, catálogo, consola y endpoints HTTP. Ejecuta `mvn test` o `mvn verify` y comprueba que Surefire descubre y ejecuta los tests. No hay herramienta de lint configurada.
- Añade pruebas de comportamiento con JUnit Jupiter cuando la lógica lo justifique. No añadas tests de relleno.
- Revisa `git diff --check`, `git diff` y `git status --short`; informa qué comprobaste y qué quedó pendiente.

## Cuidado de los cambios
- Inspecciona el estado de Git antes de editar y conserva cambios del usuario.
- Antes de editar configuración existente, guarda una copia y explica el cambio. No incluyas copias con secretos en Git.
- No muestres secretos ni modifiques credenciales.
- Pide confirmación antes de eliminar contenido, publicar, enviar mensajes, conceder permisos amplios o instalar servidores MCP externos.
- No hagas push directo a main ni reescribas historial. Realiza commits, publicaciones o despliegues solo dentro del alcance autorizado.

## Flujo de GitHub
- Sigue CONTRIBUTING.md: issue para funcionalidades/bugs, rama corta, validación y PR hacia main.
- Antes de empezar una implementación, comprueba si ya existe una rama para esa tarea. No trabajes directamente sobre main.
- Para validar una PR usa `mvn --batch-mode --no-transfer-progress verify` y ejecuta Main con entrada de menú determinista; informa del resultado de los tests y del smoke check.
- No publiques ramas, issues o PR, ni hagas merge o cambies ajustes remotos, sin confirmación del usuario.
- No confundas archivos locales de CI con una ejecución de GitHub Actions o una protección remota ya verificada.
