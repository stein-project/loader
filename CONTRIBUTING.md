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
Bridge and the SDK next to this repository. While those repositories are private, the secrets `BRIDGE_DEPLOY_KEY` and
`SDK_DEPLOY_KEY` hold read-only deploy keys for them. Without the secret, the checkout uses the workflow token, which
reads public repositories: once the siblings are public the secrets can be deleted, and pull requests from forks
(which never get secrets) build as soon as the siblings are public. `release.yml` publishes nothing: it keeps
`steinloader.jar`, `stein-api.jar`, `stein-api-sources.jar`, `LICENSE`, `LICENSE-API`, `NOTICE` and `SHA256SUMS` as a
workflow artifact. Players get the Loader inside the installer (released by the installer's workflow to
Stein-Project/stein-loader) and mod authors get `stein-api` from the SDK's Maven repository. The jars carry their
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
README. `stein.json` (what every installed Loader fetches at startup) is **not** in this repository: it lives in the
public distribution repository [Stein-Project/stein-loader](https://github.com/Stein-Project/stein-loader), where
`discord` and `news` are edited by hand and `latest` and `mods` are written by the release workflows. A change there
takes effect for every player on their next start, so keep it to what the README describes.

## Versions

`Loader.VERSION` is bumped once per Loader release, before the first change that goes into the next one; new API
members get `@Experimental(since = "<that version>")`. Mods ask for a minimum Loader version in `steinmod.json`
(`"loader": "0.1.24"`).

## The obfuscated build (what reaches players)

The Loader reaches players only inside the installer, and that copy is obfuscated (`stein-api.jar` is not: mods
compile against it):

```sh
./gradlew dist                                         # build/dist/steinloader.jar, mapping in build/dist-mapping
./gradlew runClient -Pstein.dist                       # plays the obfuscated Bridge + Loader
./gradlew verify -Pstein.world=<world> -Pstein.dist    # the in-game verification on the obfuscated build
```

- `dist` runs `../bridge`'s `dist` first and obfuscates the Loader on top of the Bridge's mapping (`-applymapping`):
  the Loader calls the Bridge by name, so a `build/dist/steinloader.jar` only works with the `steinbridge.jar` of the
  same dist build. The build checks that the Bridge came out with the same names and that every reference resolves.
- What keeps its name is in `loaderKeeps` in `build.gradle.kts`: the mod API (`api`), what the official mods call
  (`internal`), ASM, and what the Bridge's diagnostics reach by name. The Bridge's translated rules replace the ones
  in `steinloader/rules` (ModPatch).
- With `-Pstein.dist` the `-Dsteinbridge.*` names given to `runClient`/`verify` are translated through
  `build/dist-mapping/codes.txt`.
- A change that is fine in the plain build can break the obfuscated one (reflection or a string naming one of our
  classes, a lambda for a Bridge interface): run `verify -Pstein.dist` before a release.
- Never put anything of `build/dist-mapping` in a release or a commit.

### Reading a player's stack trace

`build/dist-mapping/steinloader.txt` maps both the Loader and the Bridge of that build; keep it for every release.
With ProGuard's retrace (`bin/retrace.sh` in the ProGuard release zip, or `com.guardsquare:proguard-retrace`):

```sh
retrace build/dist-mapping/steinloader.txt stacktrace.txt
```
