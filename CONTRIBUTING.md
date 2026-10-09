# Contributing to Stein Loader

Read the [Stein contributing guide](https://github.com/Stein-Project/.github/blob/main/CONTRIBUTING.md) first: it
covers the rules, code style, commits and the CLA. This page is what is specific to the Loader.

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
(which never get secrets) build as soon as the siblings are public. The release ships `steinloader.jar`,
`stein-api.jar`, `stein-api-sources.jar`, `LICENSE`, `LICENSE-API`, `NOTICE` and `SHA256SUMS`; the jars carry their
licences in `META-INF` too (MPL and NOTICE in the Loader, MIT in the API jars).

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

`stein.json` (what every installed Loader fetches at startup) and `changelog/` (the pack's release notes, read by
the installer's release workflow) are edited by hand: see the README. A pull request that changes `stein.json` takes
effect for every player as soon as it is merged, so keep it to what the README describes.

## Versions

`Loader.VERSION` is bumped once per Loader release, before the first change that goes into the next one; new API
members get `@Experimental(since = "<that version>")`. Mods ask for a minimum Loader version in `steinmod.json`
(`"loader": "0.1.24"`).
