# Abomasnow Ancestral — configuración de combate

Esta carpeta contiene el encuentro de Abomasnow Ancestral para FIW Bosses 1.21.1.

## Instalación

Copia los archivos así:

- `bosses/abomasnow_ancestral.json` → `config/fiw_bosses/bosses/abomasnow_ancestral.json`
- `minions/snover_guard.json` → `config/fiw_bosses/minions/snover_guard.json`
- `skins/snover_guard.png` → `config/fiw_bosses/skins/snover_guard.png`
- `skins/abomasnow_ancestral.png` → `config/fiw_bosses/skins/abomasnow_ancestral.png`

Después reinicia el servidor y ejecuta `/boss reload` y `/boss validate`.

## Fases y guardianes

El encuentro tiene cuatro fases con oleadas de 8, 12, 16 y 20 Snover. Abomasnow permanece protegido mientras quede algún guardián vivo. Al derrotar al último Snover, el escudo se rompe y el jefe vuelve a ser vulnerable.

La primera fase ya cuenta con cinco patrones de ataque: explosión AOE, carga frontal, corte cuerpo a cuerpo, pinchos de hielo y cristal helado. Las fases siguientes añaden ventisca, lluvia de carámbanos, golpe sísmico y congelación.

## Equilibrio

Los daños y cooldowns están ajustados para que el combate suponga un reto con armadura completa de diamante. La carga frontal conserva sus valores originales. Los Snover ahora tienen 40 de vida y un corte cuerpo a cuerpo de 7 de daño, con más alcance y menor tiempo de espera; el daño final debe validarse en el servidor con la protección real de la armadura.

## Mega-Abomasnow y animaciones

La cuarta fase utiliza el aspecto `mega_evolution=mega`, aplicado por Cobblemon Mega Showdown. La transformación tiene una secuencia de 90 ticks: Abomasnow se prepara y se eleva mientras se forma una espiral de nieve; en el clímax aparece una explosión helada y se cambia al modelo Mega. La animación de cuerpo completo también se utiliza para los ataques, y la explosión AOE lanza a los jugadores algo más alto.

La integración crea la entidad visual de Cobblemon mediante reflexión, sin convertir Cobblemon en dependencia obligatoria de compilación. El aspecto Mega depende de que Cobblemon Mega Showdown y la forma de Abomasnow correspondiente estén instalados y disponibles. Las animaciones son poses de cuerpo completo y efectos de partículas; todavía no son animaciones articuladas independientes de brazos y cabeza. El resultado visual y el equilibrio definitivo necesitan una prueba dentro de tu servidor y con las versiones exactas de tus mods.
