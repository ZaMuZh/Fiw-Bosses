# Abomasnow Ancestral — configuración inicial

Este directorio contiene una primera configuración jugable del jefe para FIW Bosses 1.21.1.

## Instalación

Copia los archivos así:

- `bosses/abomasnow_ancestral.json` → `config/fiw_bosses/bosses/abomasnow_ancestral.json`
- `minions/snover_guard.json` → `config/fiw_bosses/minions/snover_guard.json`
- `skins/snover_guard.png` → `config/fiw_bosses/skins/snover_guard.png`

Después ejecuta `/boss reload` y `/boss validate`.

## Oleadas y escudo

La configuración usa el sistema de escudo por fase que ya existe en FIW Bosses. En cada fase el jefe invoca 8, 12, 16 o 20 minions; el jefe permanece invulnerable mientras queden guardianes vivos. Al morir el último, el escudo cae y el combate continúa.

## Estado de esta entrega

Esta es la primera base de configuración: fases, ataques existentes y bloqueo por guardianes. El minion usa la entidad personalizada de FIW Bosses con una skin de Minecraft inspirada en Snover. No es todavía el modelo 3D nativo de Cobblemon. La barrera de nieve y los enlaces de energía sí están implementados en código. **Todavía faltan el modelo 3D real de Snover, animaciones nuevas de esqueleto, la secuencia de carámbanos proyectiles y la transformación real a Mega-Abomasnow.** Esas partes requieren más cambios de código y recursos cliente/servidor; no se deben dar por implementadas por copiar estos archivos.

La habilidad `particle_tornado` usa partículas de nieve y nube para representar la ventisca; `icicle_rain` marca el suelo y hace caer bloques de hielo reales desde arriba, que se rompen al impactar; `ground_spike` y `ice_crystal` completan los ataques de área. Los valores de daño son una primera calibración y deben probarse en el servidor con el equipo y armadura reales.
