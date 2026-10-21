<p align="center">
  <img src="docs/media/icon-animated.gif" width="256" alt="Alert Tweaks KoHs logo">
</p>

<h1 align="center">Alert Tweaks KoHs</h1>

<p align="center">
  <strong>Combat and survival alerts for what the screen does not tell you at a glance.</strong>
</p>

<p align="center">
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs"><img alt="GitHub repository" src="https://img.shields.io/badge/GitHub-Repository-181717?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs/releases"><img alt="Latest release" src="https://img.shields.io/badge/Release-2.0.0-6f2cff?style=for-the-badge&logo=github"></a>
  <a href="https://github.com/kerlycanelita/Alert-Tweaks-KoHs/issues"><img alt="Report an issue" src="https://img.shields.io/badge/Issues-Report_an_issue-c93c7a?style=for-the-badge&logo=githubissues&logoColor=white"></a>
  <a href="https://discord.gg/9t2VxEF7UU"><img alt="Discord" src="https://img.shields.io/badge/Discord-9t2VxEF7UU-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
</p>

Alert Tweaks KoHs is a client-side Fabric mod. It tints the screen edges as your health drops, plays a heartbeat that speeds up near death, points an arrow at whoever just hit you, and raises short alert cards for things you would otherwise miss in a fight: an empty offhand, armour about to break, a fire resistance running out, a fall that will kill you, a server that stopped answering.

Every alert comes from information the client already has. Nothing is sent to the server, and nothing aims, clicks or acts for you.

## Compatibility

| Minecraft | Fabric API | Java |
|---|---|---:|
| 26.3 | 0.161.0+26.3 | 25+ |
| 26.2 | 0.156.0+26.2 | 25+ |
| 26.1.2 | 0.155.2+26.1.2 | 25+ |
| 26.1.1 | 0.145.4+26.1.1 | 25+ |
| 26.1 | 0.145.1+26.1 | 25+ |
| 1.21.11 | 0.141.6+1.21.11 | 21+ |

Each Minecraft version has its own jar. Fabric API is required. Mod Menu is optional and opens the settings.

## Alerts

| Group | Alert | What it tells you |
|---|---|---|
| Totems | No totem in offhand | Your offhand totem popped or was moved, and how many are left. |
| Totems | Totems running low | Your total totem count reached the number you chose. |
| Totems | Enemy totem pops | Who popped a totem and how many times since they last died. |
| Gear | Armour breaking | A worn piece (elytra included) dropped below the durability you chose. |
| Gear | Weapon or tool breaking | Same for the item in your main hand. |
| Gear | Shield disabled | An axe hit put your shield on cooldown. |
| Gear | Cooldown ready | Ender pearl, wind charge, chorus fruit or shield can be used again. Cooldowns shorter than your setting are skipped. |
| Body | Harmful effect | You received poison, weakness, slowness, wither, levitation... or a stronger level of one. |
| Body | Effect running out | A good effect ends in the seconds you chose. |
| Body | Too hungry to sprint | Food reached 3 drumsticks, where sprinting stops. |
| Body | Dangerous fall | While falling, the damage of landing where you are heading: water, slime, hay, beds, Feather Falling, Protection and Resistance are counted. |
| Inventory and server | Supplies running low | Golden apples, pearls, crystals, obsidian, anchors, glowstone, bottles o' enchanting, wind charges or arrows you were carrying dropped to a few. |
| Inventory and server | Inventory full | No free slot left. |
| Inventory and server | Server not responding | The server stopped sending its once-a-second update; tells you when it comes back. Multiplayer only. |
| Player alerts | Joined / left / already online | Players on your watch list arriving at or leaving the server, with their face. |

Each alert can be turned off, given its own sound (alarm, bell, pling, chime, bass, chirp or none), its own look and tested from the settings. Looks: **Card** (icon, title and detail), **Compact** (a one-line pill), **Icon** (the icon alone in a diamond that breathes), **Text** (no panel, typed in like a subtitle) and **Banner** (a wide strip that opens with a flash). Danger alerts can flash the screen edges once.

### Positions

**Edit positions** opens your own screen at full size (your real HUD in a world, the menu panorama and a PvP hotbar elsewhere). Drag any card where it should appear, drop cards on each other to stack them, move the default stack with its grip, and right-click a card to send it back. A ten-second tutorial plays the first time (and from the ? button): placing a card, stacking two, moving a stack by its grip and sending a card back. A floating list holds every alert to drag onto the screen, plus card size, time on screen, volume, danger flash and a button that plays every alert in place. Stacks grow away from the crosshair and the nearest edge and never cover the hotbar.

### Player alerts

Add names to a watch list and get a card with their face when they join or leave the server you are on, and one card on joining for those already there. It reads the server's player list (Tab), never anyone's position: players hidden by vanish are not on that list, so they never show, and invisibility makes no difference. Faces come from Mojang, the same skins NameMC shows.

The same alert about the same thing refreshes its card instead of stacking, so a burst of events never floods the screen.

## Health and threat

- **Low health vignette.** Coloured fade around the edges below a health threshold, stronger as health falls. Colour, opacity, reach, smooth fade, fixed strength and a heartbeat pulse.
- **Heartbeat sound.** Its own sound event with a subtitle; with low-health linking it speeds up from 0.7 s to 0.35 s per beat and rises in pitch.
- **Threat indicator.** A marker around the crosshair for each player who hits you, for five seconds, following them while it lasts. The attacker is never outlined or shown through blocks. Up to four at once. Eight animated styles: Classic, Arc, Chevrons, Slash, Radar, Weapon (what hit you, taken at the moment of the hit), Edge (a glow on the screen border) and Shard.

## Settings

Open them from Mod Menu. Five pages - Health, Threat, Alerts, Player alerts and KoHs - with an option tree on the left and, on the right, a live miniature of your own screen: your real hotbar, health and food in a world, a random crystal, mace or nethpot hotbar elsewhere. Settings are saved in `config/alert_tweaks.json`; a `healt_tweaks.json` from the old mod is imported once.

## What it does not change

The mod sends nothing and only reads state the client already holds. It does not touch aim, reach, cooldowns, attack timing, hitboxes or packets. Two things go a little past the vanilla screen: the threat arrow keeps pointing at the attacker if they move behind blocks while it lasts, and enemy pops are counted from the totem animation every client already receives. Servers may restrict client mods whatever they do; follow each server's rules.

## Building

One source tree builds every version with [Stonecutter](https://stonecutter.kikugie.dev/); the code is written for 26.2 and `//?` comments adapt it.

    ./gradlew buildAll                 # every version, jars copied to dist/<version>/
    ./gradlew :26.2:build              # one version
    ./gradlew :26.2:runClientGameTest  # opens the game, clicks through the settings, fires real alerts, takes screenshots

`build` also runs `checkLayout`, which lays out the settings screen at every GUI size from 240x140 to 1920x1080 and fails on any overlap. JDK 25 runs Gradle for every version.

## Support

- Bugs: [GitHub Issues](https://github.com/kerlycanelita/Alert-Tweaks-KoHs/issues)
- Help and ideas: [Discord](https://discord.gg/9t2VxEF7UU)

## License

[MIT](LICENSE). Copyright © 2026 zymekoh.
