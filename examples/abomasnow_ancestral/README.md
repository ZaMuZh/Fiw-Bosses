# Abomasnow Ancestral — configuración inicial

Este directorio contiene una primera configuración jugable del jefe para FIW Bosses 1.21.1.

## Instalación

Copia los archivos así:

- `bosses/abomasnow_ancestral.json` → `config/fiw_bosses/bosses/abomasnow_ancestral.json`
- `minions/snover_guard.json` → `config/fiw_bosses/minions/snover_guard.json`
- `skins/snover_guard.png` → `config/fiw_bosses/skins/snover_guard.png`
- `skins/abomasnow_ancestral.png` → `config/fiw_bosses/skins/abomasnow_ancestral.png`

Después ejecuta `/boss reload` y `/boss validate`.

## Oleadas y escudo

La configuración usa el sistema de escudo por fase que ya existe en FIW Bosses. En cada fase el jefe invoca 8, 12, 16 o 20 minions; el jefe permanece invulnerable mientras queden guardianes vivos. Al morir el último, el escudo cae y el combate continúa.

## Estado de esta entrega

Esta es la primera base de configuración: fases, ataques existentes y bloqueo por guardianes. El renderer ahora intenta crear entidades visuales reales de Cobblemon mediante `PokemonProperties` por reflexión: Abomasnow para el jefe, Snover para los guardianes y `form=mega` en la cuarta fase. Esto evita añadir Cobblemon como dependencia obligatoria de compilación. La skin incluida sirve como respaldo visual si no está disponible la integración. **La creación de los Pokémon y la forma Mega aún necesitan prueba dentro de tu instalación exacta de Cobblemon; no he podido compilar ni arrancar el servidor desde este entorno.** Se han añadido poses de ataque de cuerpo completo (golpe, embestida, ataque a distancia y despertar de fase) al renderizador de disfraces en Fabric y NeoForge 1.21.1. Todavía faltan las animaciones articuladas por huesos —brazos y cabeza con movimiento independiente—, portar esas poses a los renderizadores 1.20.1/1.21.8/1.21.11 y una malla de carámbano puntiaguda propia.

La habilidad `particle_tornado` usa partículas de nieve y nube para representar la ventisca; `icicle_rain` marca el suelo y hace caer bloques de hielo reales desde arriba, que se rompen al impactar; `ground_spike` y `ice_crystal` completan los ataques de área. Los valores de daño son una primera calibración y deben probarse en el servidor con el equipo y armadura reales.
