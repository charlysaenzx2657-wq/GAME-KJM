# Game Optimizer (Free Fire) — con Shizuku

App lanzadora que optimiza el sistema antes de abrir el juego y muestra FPS reales, sin necesidad de root, usando **Shizuku**.

## Qué hace

1. Verifica que Shizuku esté instalado y activo.
2. Al presionar "Optimizar y lanzar Free Fire":
   - Cierra apps en segundo plano que consumen RAM/CPU (WhatsApp, Instagram, YouTube, etc.)
   - Desactiva animaciones del sistema.
   - Intenta forzar el modo de rendimiento alto (depende del fabricante/chipset).
   - Lanza el juego.
   - Muestra un overlay flotante con el FPS real, leído de `dumpsys gfxinfo` vía Shizuku.
3. Botón para restaurar los ajustes del sistema al salir del juego.

## Cómo subir esto a GitHub y compilar el APK automáticamente

1. Crea un repositorio nuevo en GitHub (puede ser privado).
2. Sube todos estos archivos y carpetas tal cual están (respetando la estructura).
3. En GitHub, ve a la pestaña **Actions** del repo — el workflow `Build APK` correrá solo con cada push a `main`.
4. Cuando termine (ícono verde ✅), entra al run → sección **Artifacts** → descarga `app-debug-apk`.
5. Descomprime y ahí está tu `app-debug.apk`, listo para instalar en tu Moto G04s.

Si prefieres compilarlo manualmente sin esperar un push, ve a Actions → Build APK → "Run workflow".

## Cómo activar Shizuku en el teléfono (una vez por reinicio)

1. Ajustes → Sistema → Acerca del teléfono → toca 7 veces "Número de compilación" para activar Opciones de Desarrollador.
2. Ajustes → Sistema → Opciones de desarrollador → activa **Depuración inalámbrica**.
3. Toca sobre "Depuración inalámbrica" → **Emparejar dispositivo con código de emparejamiento**.
4. Anota el código de 6 dígitos y la IP:puerto que aparecen.
5. Abre la app **Shizuku** (instálala desde Play Store si no la tienes) → "Iniciar mediante emparejamiento inalámbrico por Wi-Fi" → pega el código, IP y puerto.
6. Abre **Game Optimizer** → debería detectar el servicio activo. Si pide permiso, acéptalo.

**Nota:** hay que repetir el emparejamiento cada vez que reinicies el teléfono, es una limitación de Android, no de la app.

## Cambiar el paquete del juego

Si el paquete de Free Fire no es `com.dts.freefireth` en tu versión (normal/MAX/etc.), cámbialo en:
`app/src/main/java/com/gameoptimizer/app/ui/MainActivity.kt` → variable `targetGamePackage`.

## Limitaciones conocidas

- El modo de rendimiento fijo (`cmd power set-fixed-performance-mode-enabled`) no tiene efecto garantizado en todos los chipsets; en Unisoc (como tu T606) el soporte es más limitado que en Snapdragon/Exynos.
- No es posible modificar los ajustes gráficos internos de Free Fire (resolución, calidad) desde otra app — eso vive dentro del sandbox del propio juego.
- El overlay de FPS requiere que Shizuku siga activo durante toda la sesión de juego.
