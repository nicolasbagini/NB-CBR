# Visor CBR (app de Android) - versión para subir desde el teléfono

Todos los archivos están sueltos (sin carpetas) para poder subirlos desde el teléfono.
El flujo de GitHub los ordena solo.

1. Sube todos los archivos de esta carpeta MENOS `build.yml` con "uploading an existing file".
2. Crea el archivo `.github/workflows/build.yml` (Add file > Create new file) y pega dentro el contenido de `build.yml`.
3. Mira la pestaña Actions: debería empezar a construir solo.
4. Al terminar (marca verde), descarga `visor-cbr-apk` de la sección Artifacts, descomprímelo e instala `app-debug.apk`.
