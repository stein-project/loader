# Stein Loader

**A mod platform for the Minecraft 1.8.9 client, running on [Stein Bridge](https://github.com/Stein-Project/bridge).**

Stein Loader loads `.steinmod` files from the game's mods folder and gives them a small, stable API: events, HUD
elements, settings pages, key bindings, per-mod config and secrets. It also brings Stein's menus and its in-game
panel.

- **No coremods, no Mixin.** Mods cannot rewrite game bytecode, so two mods never fight over the same code, and the
  engine underneath can keep changing without breaking them. Mods change the game where the Loader gives them an
  event.
- **Java 25.** Mods are written with modern Java and readable game names (`Minecraft.getMinecraft()`), like Forge
  mods, and are translated to the game's names at build time.
- **Drawing goes through the API.** `Render` (screen), `WorldRender` (world) and `Mesh` draw the same on Stein GFX
  and on the game's fixed-function pipeline. The Loader adapts each mod to Stein Bridge when it loads it (`ModPatch`),
  but a mod that calls old fixed-function OpenGL directly (`glBegin`, `glNewList`...) is left out on Stein GFX and
  listed as incompatible, instead of closing the game.

Want to write a mod? Start with the **[Stein SDK](https://github.com/Stein-Project/sdk)**.

> Stein is not an official Minecraft product. It is not approved by or associated with Mojang or Microsoft.

## Building

The Loader compiles against the Bridge and uses the SDK's name tools, so clone the three repositories side by side:

```
stein/
├── bridge/   github.com/Stein-Project/bridge
├── loader/   this repository
└── sdk/      github.com/Stein-Project/sdk
```

You need a **JDK 17 or newer** to start Gradle (`JAVA_HOME`). The build itself runs on **JDK 25**: Gradle uses one
already installed or downloads it (`gradle/gradle-daemon-jvm.properties`). It builds on Windows, Linux and macOS.

The game comes from a Minecraft 1.8.9 installation: the launcher's usual folder by default. Without one, download it
from Mojang in the Bridge and point the build at it (this is what CI does):

```sh
(cd ../bridge && ./gradlew fetchMinecraft)          # Minecraft 1.8.9 into bridge/build/mc, sha1-checked
./gradlew build -Pstein.mcDir=../bridge/build/mc    # every command below takes it too
```

Then:

```sh
# in loader/ (the Bridge and the SDK are included as composite builds)
./gradlew build            # build/libs/steinloader.jar, build/libs/stein-api.jar, API and access checks, preflight
./gradlew runClient        # runs the game through the Bridge, with the Loader
```

`-Pstein.bridgeDir=...` and `-Pstein.sdkDir=...` point to the other repositories if they are somewhere else. To run
with mods from a folder, pass `-Pstein.mods=<folder>`; other options are `-Pstein.jvmArgs="-D..."`,
`-Pstein.gameDir`, `-Pstein.mem` and `-Pstein.user`.

The build also checks that the public API (`dev.xavier.stein.loader.api`) did not change by accident: it is
compared with `api/stein-api.txt`. When a change is intended, run `./gradlew build -Pstein.apiUpdate` and commit the new
list.

## Repository layout

| Path | What it is |
|---|---|
| `src/main/java/dev/xavier/stein/loader/api` | The mod API (what `stein-api.jar` contains) |
| `src/main/java/dev/xavier/stein/loader` | The loader, `ModPatch`, menus, HUD and the in-game panel |
| `api/stein-api.txt` | The API signature list the build checks against |
| `test/` | Access and dependency tests, and a hook test mod |
| `changelog/` | Release notes of the **pack** (see below), in English and Portuguese |
| `tools/` | Build tools: the API check, the API reference check, the `-D` option list, the Discord mark |
| `docs/OPTIONS.md` | Every `-D` option the Loader reads (generated) |

## Release notes (`changelog/`)

`changelog/<version>.en_US.md` (and the optional `<version>.pt_BR.md`) are the notes of a **pack release**, the version players see
in the installer (not the Loader's own `Loader.VERSION`). They live here, but the [installer](https://github.com/Stein-Project/installer)'s
release workflow is what reads them: it checks out this repository and puts `changelog/<tag version>.*.md` in the
release notes. Write both files before tagging an installer release; the Loader's build does not use them.

## The project file (`stein.json`)

At startup the Loader fetches `stein.json` from the default branch of the public distribution repository
[Stein-Project/stein-loader](https://github.com/Stein-Project/stein-loader)
(`https://raw.githubusercontent.com/Stein-Project/stein-loader/main/stein.json`, see `Remote.java`), not from this
repository: the Discord invite (the button on the main menu), the latest pack version (the update
warning) and a message for the main menu, with one text per language. The same file carries the installer's mod
index (`mods`), which the Loader ignores. It takes effect on the next start of everyone who has the Loader, with no
new release:

- `discord` and `news` are edited **by hand**, in that repository; give `news` a new `id` to show a new message (a
  player who closed a message does not see the same `id` again);
- `latest.version` and `latest.url` are written by the installer's release workflow when a pack release goes out;
- `mods` is written by the mods' release workflows; do not edit it by hand;
- only https links to the project's hosts are accepted (Discord, GitHub).

The last copy that arrived is kept in `config/steinloader-remoto.json` and is what applies offline, and also when the
fetch gets anything other than 200. Keys the Loader does not read are ignored (older files also had `servers`, which
is no longer used).

## Development

Bug reports, ideas and pull requests are welcome. Read the
[contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md) first; your first pull
request asks you to sign the [CLA](https://github.com/Stein-Project/.github/blob/main/CLA.md). What is specific to the
Loader is in [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

Stein Loader is licensed under the [Mozilla Public License 2.0](LICENSE), except the mod API
(`dev.xavier.stein.loader.api`), which is licensed under the [MIT License](LICENSE-API) so mods can use any licence.
Third-party notices are in [NOTICE](NOTICE). The Stein name and logo are not covered by the licences: see the [trademark policy](https://github.com/Stein-Project/.github/blob/main/TRADEMARK.md).

Created by [Breno Xavier](https://github.com/brenoxavier).
