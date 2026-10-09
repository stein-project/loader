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
- **Your GL calls get the engine's batching for free:** the Loader adapts each mod to Stein Bridge when it loads
  it (`ModPatch`).

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

You need a **JDK 17 or newer** to start Gradle (`JAVA_HOME`; Gradle finds or downloads the **JDK 25** the Loader is
compiled with) and Minecraft 1.8.9 installed (or `./gradlew fetchMinecraft` in the Bridge). It builds on Windows, Linux
and macOS. Then:

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
| `changelog/` | Release notes, in English and Portuguese |

## Contributing

Issues and pull requests are welcome. Read the [contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md)
first. Contributions require signing the [Contributor License Agreement](https://github.com/Stein-Project/.github/blob/main/CLA.md).

## Licence

Stein Loader is licensed under the [Mozilla Public License 2.0](LICENSE), except the mod API
(`dev.xavier.stein.loader.api`), which is licensed under the [MIT License](LICENSE-API) so mods can use any licence.
Third-party notices are in [NOTICE](NOTICE). The Stein name and logo are not covered by the licence: see the [trademark policy](TRADEMARK.md).

Created by [Breno Xavier](https://github.com/brenoxavier).
