# Contributing to Stein Loader

Read the [Stein contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md) first: it
covers the rules, code style, commits and the [CLA](https://github.com/Stein-Project/.github/blob/main/CLA.md) your first pull request asks
you to sign. This page is what is specific to the Loader.

## Build and check

With [bridge](https://github.com/Stein-Project/bridge) and [sdk](https://github.com/Stein-Project/sdk) cloned
next to this repository (they are included as composite builds; nothing to build by hand):

```sh
./gradlew spotlessApply                 # google-java-format --aosp on every Java file
./gradlew build                         # Loader, API jar, API/access/dependency tests, preflight
./gradlew runClient                     # plays the game with the Loader
./gradlew verify -Pstein.world=<world>  # plays <world> with every verification mode on, both pipelines; fails on divergence
```

`./gradlew verify` needs a singleplayer world in `build/gamedir/saves/<world>` (any world works; one with some
mobs and other players around exercises more of the renderer).
A run fails when its log has an exception or a divergence, when the JVM itself died (`FATAL ERROR`, `hs_err`), when
the game exited with an error before its time was up, or when it printed none of the check lines it should have.

Without Minecraft 1.8.9 installed in the launcher's folder, run `./gradlew fetchMinecraft` in `../bridge` and add
`-Pstein.mcDir=../bridge/build/mc` to the commands above. `./gradlew options` rewrites `docs/OPTIONS.md` after a
`-D` option is added to `LoaderOptions` (the build fails while it is out of date).

## CI

`.github/workflows/build.yml` (every push and pull request) and `release.yml` (a `v<version>` tag) check out the
Bridge and the SDK (both public) next to this repository; a fork needs no secret. `release.yml` publishes nothing: it keeps
`steinloader.jar`, `stein-api.jar`, `stein-api-sources.jar`, `LICENSE`, `LICENSE-API`, `NOTICE` and `SHA256SUMS` as a
workflow artifact. Players get the Loader inside the installer (released by the installer's workflow on
Stein-Project/installer) and mod authors get `stein-api` from the SDK's Maven repository. The jars carry their
licences in `META-INF` too (LICENSE and NOTICE in the Loader, MIT in the API jars).

## The mod API

`src/main/java/dev/xavier/stein/loader/api` is the API mods compile against (MIT-licensed, shipped as
`stein-api.jar` in the SDK).

- `./gradlew build` compares it with `api/stein-api.txt` and fails on any change. When a change is intended and
  discussed, run `./gradlew build -Pstein.apiUpdate` and commit the updated list in the same pull request.
- Never remove or change a public signature without an issue and a decision: it breaks mods already published.
- New members get `@Experimental(since = "<next version>")`.
- The API does not touch game classes: it goes through `api.spi.Host`, which the Loader implements
  (`LoaderHost`). A new feature usually means a `Host` method plus its implementation.
- Every new API feature needs Javadoc and, when it is something mod authors will look for, a line in the SDK guide.
- `./gradlew build` also builds the API's Javadoc with doclint (`apiJavadoc`): a public member without a comment, a
  broken `{@link}` or bad HTML fails it.
- The list records constant values and the order of enum constants too: javac copies both into the mods.
- New members also stop the build until the list is updated: an addition is reviewed, not taken in silently.

## Player data

`config/steinloader.json`, the HUD layout file, translation keys and mod ids are data players already have.
Add new keys instead of renaming existing ones.

## The project file and the release notes

`changelog/` (the pack's release notes, read by the installer's release workflow) is edited by hand here: see the
README. `stein.json` (what every installed Loader fetches at startup) is **not** in this repository: it lives in
[Stein-Project/installer](https://github.com/Stein-Project/installer), where `discord`, `news` and `sources` are edited
by hand and every installer release carries it with `latest` filled in. A change there
takes effect for every player on their next start, so keep it to what the README describes.

## Versions

`Loader.VERSION` is bumped once per Loader release, before the first change that goes into the next one; new API
members get `@Experimental(since = "<that version>")`. Mods ask for a minimum Loader version in `steinmod.json`
(`"loader": "0.1.24"`).

## What reaches players

Players get `build/libs/steinloader.jar` as it is built, inside the installer, next to the Bridge. The Loader calls
the Bridge by its real names, and ModPatch reads the patcher rules from the Bridge jar (`steinbridge/rules/`), not
from this one: a Loader always adapts mods with the rules of the Bridge it runs on. `Loader.MIN_BRIDGE` is the oldest
Bridge it accepts; read the running Bridge's version through `Loader.bridgeVersion()` (`SteinBridge.VERSION` is a
constant, and javac would copy the version the Loader was built against).
