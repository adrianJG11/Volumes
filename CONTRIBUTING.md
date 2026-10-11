# Trabajar en la biblioteca

Usamos un flujo pequeño: issue, rama, cambio, validación, pull request y merge.
Mantén una sola rama de trabajo activa mientras aprendes.

## Empezar una tarea

Para funcionalidades, errores o cambios de estructura, crea una issue con un objetivo y criterios de aceptación.
Una corrección documental pequeña puede ir directamente a una PR sin issue.

Desde Ubuntu/WSL, y con el árbol de trabajo limpio:

```bash
git switch main
git pull --ff-only
git switch -c feat/buscar-libros
```

Usa un nombre acorde con la tarea: `feat/...`, `fix/...`, `docs/...` o `chore/...`.
No necesitas una rama permanente develop.

## Validar y guardar

```bash
mvn --batch-mode --no-transfer-progress verify
java -cp target/classes com.adrian.biblioteca.Main
git diff --check
git diff
git status --short
```

Actualmente no hay tests automatizados. Maven compila y empaqueta; la ejecución de Main comprueba que la aplicación arranca.
Introduce tests de comportamiento cuando añadas lógica que los justifique; no confundas un build correcto con cobertura de pruebas.

Añade solo los archivos de la tarea y revisa lo que entrará en el commit:

```bash
git add ruta/del/archivo
git diff --cached
git commit -m "feat: describe el cambio"
```

## Publicar y revisar

Cuando quieras publicar tu rama:

```bash
git push -u origin HEAD
```

Abre una PR hacia main en GitHub. Explica el cambio y su validación, y usa `Closes #numero` cuando resuelva la issue.
Revisa tú mismo la pestaña Files changed y espera al check **Build and smoke test**.
Codex puede ayudar a revisar; debes comprender y comprobar el resultado.
Para trabajo individual no exigimos la aprobación de otro usuario.

Tras fusionar mediante **Squash and merge**, vuelve a main y actualiza con `git pull --ff-only`.
Puedes retirar la rama terminada cuando hayas comprobado que el cambio está integrado.

## CI y protección de main

El workflow Java CI valida PR hacia main y los cambios que llegan a main con Java 25 y Maven.
Solo necesita permiso de lectura del repositorio, sin secretos personalizados ni despliegues.

La protección remota de main se configura en GitHub, no por añadir este archivo.
Configuración propuesta: PR obligatoria, check **Build and smoke test** obligatorio, rama actualizada,
conversaciones resueltas, cero aprobaciones obligatorias y sin force push ni eliminación de main.
Activa el requisito del check después de su primera ejecución correcta. Su disponibilidad depende
de la visibilidad del repositorio y el plan de GitHub. No cambies la visibilidad para habilitarlo.

Los agentes deben pedir confirmación antes de publicar, fusionar o modificar ajustes remotos.
