# AGENTS.md — Biblioteca personal

## Contexto y alcance
- Este repositorio es un proyecto personal de aprendizaje y una biblioteca en construcción.
- Aplicación de consola con Java 25 y Maven, bajo `com.adrian.biblioteca`: `Main`, `book.Book`, `library.Library` y `cli.LibraryConsole`.
- Comprueba el código, README y pom.xml al empezar: el proyecto puede evolucionar.
- Spring Boot y recomendaciones/IA en Python son posibles fases futuras, no dependencias ni requisitos actuales. No introduzcas servicios, persistencia ni infraestructura por anticipado.
- MyVault es otro proyecto y está terminado; no mezcles sus datos, requisitos ni tareas.

## Colaboración y aprendizaje
- Responde en español y explica las decisiones técnicas con ejemplos concretos.
- Para consultas o ejercicios, ofrece primero una explicación y pistas; deja que el usuario practique. Cuando solicite implementación, realiza el cambio acotado y explica cómo funciona y cómo comprobarlo.
- Trabaja en pasos pequeños y verificables. Pregunta solo por decisiones que bloqueen el paso actual.
- Reutiliza lo existente. Justifica nuevas dependencias y evita refactorizaciones ajenas al objetivo.
- No inventes progreso ni marques tareas como terminadas sin una comprobación.

## Entorno y desarrollo
- Entorno actual: Ubuntu en WSL, repositorio en `/home/adrian/projects/biblioteca`.
- Ejecuta Java, Maven y Git dentro de WSL. La carpeta de Windows llamada `biblioteca python+java` no es esta copia del repositorio.
- Usa las versiones y convenciones declaradas en pom.xml. No cambies el JDK ni agregues un framework sin una necesidad acordada.
- No edites `target/` ni archivos `.class`: son resultados de compilación.
- Consulta documentación oficial cuando una decisión dependa de versiones o compatibilidad.

## Verificación
- Para cambios Java, desde la raíz: `mvn compile`.
- Prueba de ejecución actual: `java -cp target/classes com.adrian.biblioteca.Main`.
- Hay tests JUnit Jupiter en `src/test/java` para Book, Library y LibraryConsole. Ejecuta `mvn test` o `mvn verify` y comprueba que Surefire descubre y ejecuta los tests. No hay herramienta de lint configurada.
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
