<p align="center">
  <img src="versions/26.1.2/src/main/resources/assets/alert_tweaks/icon.png" width="360" alt="Alert Tweaks KoHs logo">
</p>

<h1 align="center">Alert Tweaks KoHs</h1>

<p align="center">
  <strong>Low-health vignette, heartbeat alert, and a directional indicator that points at whoever just hit you.</strong>
</p>

<p align="center">
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs"><img alt="GitHub repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs/releases"><img alt="Latest release" src="https://img.shields.io/badge/Release-1.1.0-6f2cff?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs/issues"><img alt="Report an issue" src="https://img.shields.io/badge/Issues-Report_an_issue-c93c7a?style=for-the-badge&logo=githubissues&logoColor=white"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img alt="Discord" src="https://img.shields.io/badge/Discord-9t2VxEF7UU-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
</p>

Alert Tweaks KoHs is a Fabric client-side mod that draws combat awareness on the HUD without touching gameplay. It tints the screen edges as your health drops, plays a heartbeat that speeds up the closer you get to dying, and shows an on-screen arrow for each player who lands a hit on you.

It is the successor to **Healt Alert Tweaks** (`healt_tweaks`), which only survived as a compiled 1.21.6–1.21.8 jar. The code was rebuilt from that build and the threat indicator was added on top. Existing `config/healt_tweaks.json` settings are imported automatically the first time the mod runs.

This repository contains released implementations for **Minecraft 26.1.2 and 1.21.11**. Both targets ship the same features. 26.1.2 is the maintained branch; every target lives in its own directory under `versions/`.

## Compatibility

| Minecraft | Fabric API | Java | Source |
|---|---|---:|---|
| **26.1.2** | **0.155.2+26.1.2** | **25+** | `versions/26.1.2` |
| **1.21.11** | **0.141.6+1.21.11** | **21+** | `versions/1.21.11` |

Both builds require Fabric Loader 0.19.3 or newer and Fabric API. Mod Menu is optional and is how the configuration screen is reached.

The mod is client-side only and does not need to be installed on the server.

## Features

- **Low Health Vignette.** Colored fade around the screen edges once health drops below a chosen threshold, ramping from 12% to full strength as health approaches zero. Configurable color, opacity, reach, smooth fade in and out, a fixed-intensity mode, and an optional heartbeat pulse that beats faster and harder the lower the health.
- **Heartbeat Sound.** A dedicated sound event (`alert_tweaks:heartbeat`) with its own volume and tempo controls. With low-health linking on, the interval tightens from 0.7 s at the threshold down to 0.35 s near death and the pitch rises with it. Carries a subtitle for players who play with subtitles on.
- **Threat Indicator.** An arrow around the crosshair for each player who hits you, described in full below.
- **Live preview.** The configuration screen previews the vignette and loops the full threat animation, so color, opacity, reach, and indicator size can be judged without taking a hit.

## Threat Indicator

When another player damages you, an arrow appears around the crosshair pointing at them — including when the hit comes from behind, exactly like the damage indicator of any shooter.

- **The arrow follows the attacker while it lasts.** Their position is refreshed every tick from the entity the client already tracks, so it points at where they are, not at where the hit landed. If the attacker unloads, dies, or leaves range, the arrow freezes at the last known position.
- **The attacker is never drawn.** No outline, no highlight, no rendering through blocks. The only thing added to the screen is the arrow.
- **Five seconds per hit**, then a fade-out. Each new hit from the same player refreshes their own arrow rather than stacking another. Up to four attackers are tracked at once; a fifth pushes out the stalest.
- **Animation.** The arrow punches out of the crosshair behind a shockwave with a slight overshoot, breathes while it holds, and on the way out drifts outward and grows while a second ring expands behind it.

### Sizing and placement

- **Adaptive Size** — on by default. The arrow is sized as a constant fraction of the GUI height, which cancels out the GUI scale and keeps it the same physical size on screen at any setting.
- **Scale** — 50% to 250%. Moving this slider turns Adaptive Size off, which is the intended way to take manual control.
- **Distance** — 0% to 100%, how far from the crosshair the arrows orbit. At 0% they sit just clear of the crosshair without covering it; at 100% they push out towards the edge of the view. It follows the scale, so changing size keeps the ring proportional.

### How the hit is detected

No mixins. Every client tick the mod reads `player.hurtTime`: it is set to `hurtDuration` when a damage event arrives and counts down afterwards, so any jump upwards means a fresh hit landed. The attacker then comes from `getLastDamageSource()`, which vanilla fills in on the client when it handles the damage packet the server already sends to the victim.

For a projectile the causing entity is used rather than the direct one, since the arrow itself is already on top of you and would give a meaningless direction.

## What it does not change

Alert Tweaks KoHs sends nothing to the server and reads no packets of its own. It does not touch aim, reach, cooldowns, attack timing, hitboxes, packet timing, or packet count. Everything it draws comes from state the client already holds.

One consequence worth stating plainly: while an arrow is alive it reports the attacker's current position even if they move behind blocks. That is more than vanilla's red damage tilt tells you, which only encodes the direction the hit came from. Servers may restrict client mods regardless of what they do, so follow each server's rules.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for your Minecraft version.
2. Install the matching Fabric API release from the compatibility table.
3. Put the matching `alert-tweaks-<version>.jar` in the instance's `mods` folder.
4. Optionally install Mod Menu to open the configuration screen from the mod list.

## Configuration

Settings live in `config/alert_tweaks.json` and are written when the screen closes, not on every slider movement. The screen has two tabs:

- **Tweaks** — vignette color, threshold, opacity, reach, smooth fade, static color, heartbeat pulse, and the Threat Indicator section (adaptive size, scale, and distance from the crosshair).
- **Sound** — heartbeat volume, tempo, low-health linking, and a Play button that previews four beats with the current settings.

## Building

    $env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.2"
    .\gradlew.bat clean build

Run it from `versions/26.1.2` or `versions/1.21.11`; each JAR is written to that directory's `build/libs/`. The file ending in `-sources.jar` is not the playable build.

JDK 25 is needed to run Gradle for both targets even though the 1.21.11 build compiles against Java 21.

The two indicator sprites are generated, not hand-drawn. `tools/GenerateThreatSprites.java` redraws them:

    java tools/GenerateThreatSprites.java "versions/26.1.2/src/client/resources/assets/alert_tweaks/textures/gui"

## Support

- Reproducible bugs: [GitHub Issues](https://github.com/kerlycanelita/Alert-Tweaks-KoHs/issues)
- Community and help: [Discord](https://discord.gg/9t2VxEF7UU)

## License

Distributed under the [MIT License](LICENSE). Copyright © 2026 zymekoh.
