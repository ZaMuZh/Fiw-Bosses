# Abomasnow Ancestral — configuración de combate

Esta carpeta contiene el encuentro de Abomasnow Ancestral para FIW Bosses 1.21.1.

## Instalación

Copia los archivos así:

- `bosses/abomasnow_ancestral.json` → `config/fiw_bosses/bosses/abomasnow_ancestral.json`
- `minions/snover_guard.json` → `config/fiw_bosses/minions/snover_guard.json`
- `skins/snover_guard.png` → `config/fiw_bosses/skins/snover_guard.png`
- `skins/abomasnow_ancestral.png` → `config/fiw_bosses/skins/abomasnow_ancestral.png`

El JAR incluye además una asignación de característica de Cobblemon para Abomasnow (`mega_evolution`), necesaria porque Mega Showdown aporta el modelo Mega pero no asigna esa característica a Abomasnow en su lista de megas ZA.

Después reinicia el servidor y ejecuta `/boss reload` y `/boss validate`.

## Fases y guardianes

El encuentro tiene cuatro fases con oleadas de 4, 6, 8 y 10 Snover. Abomasnow permanece protegido mientras quede algún guardián vivo. Al derrotar al último Snover, el escudo se rompe y el jefe vuelve a ser vulnerable.

La primera fase ya cuenta con cinco patrones de ataque: explosión AOE, carga frontal, corte cuerpo a cuerpo, pinchos de hielo y cristal helado. Las fases siguientes añaden ventisca, lluvia de carámbanos, golpe sísmico y congelación. La ventisca final ya no es solo visual: su zona de daño cubre la base del tornado, golpea periódicamente, ralentiza y atrae/eleva a los jugadores hacia el remolino.

## Equilibrio

Los daños y cooldowns están ajustados para que el combate suponga un reto con armadura completa de diamante. La carga frontal conserva sus valores originales. Los Snover tienen 40 de vida y buscan jugadores por sí mismos en un radio de 20 bloques. Al alcanzar a un jugador, realizan un golpe cuerpo a cuerpo cada 20 ticks (1 segundo) que inflige 3 puntos de daño fijo (1,5 corazones), sin reducción por los puntos normales de armadura; no empujan al jugador y muestran partículas de impacto. No atacan a jugadores en creativo ni espectadores.

## Mega-Abomasnow y animaciones

La cuarta fase utiliza el aspecto `mega_evolution=mega`. FIW Bosses registra también la característica `mega_evolution` para Abomasnow, que no estaba incluida en la asignación de megas ZA de Mega Showdown. El modelo Mega requiere los recursos de Abomasnow Mega presentes en la versión instalada de Mega Showdown. La transformación tiene una secuencia de 90 ticks: Abomasnow se prepara y se eleva mientras se forma una espiral de nieve; en el clímax aparece una explosión helada y se cambia al modelo Mega. La animación de cuerpo completo también se utiliza para los ataques, y la explosión AOE lanza a los jugadores algo más alto.

La integración crea la entidad visual de Cobblemon mediante reflexión, sin convertir Cobblemon en dependencia obligatoria de compilación. El aspecto Mega depende de que Cobblemon Mega Showdown y la forma de Abomasnow correspondiente estén instalados y disponibles. Las animaciones son poses de cuerpo completo y efectos de partículas; todavía no son animaciones articuladas independientes de brazos y cabeza. El resultado visual y el equilibrio definitivo necesitan una prueba dentro de tu servidor y con las versiones exactas de tus mods.
