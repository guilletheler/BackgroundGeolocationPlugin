---
description: Reglas para el uso estricto de pnpm en el proyecto
trigger: always_on
---

# Uso de pnpm

1. **Gestor de Paquetes:** Utiliza SIEMPRE `pnpm` en lugar de `npm` o `yarn`.
2. **Scripts en package.json:** Si modificas o creas `scripts` en el `package.json`, asegúrate de que utilicen `pnpm run <script>` (o comandos directos de `pnpm`) en lugar de `npm run`.
3. **Campo packageManager:** Todos los `package.json` de este proyecto deben definir explícitamente la versión de pnpm a través de `"packageManager": "pnpm@11.25.0"` (u otra versión actual).
4. **Instalación de Dependencias:** Ejecuta siempre `pnpm install`, `pnpm add`, etc., y no utilices los comandos de npm nativos.
