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

## Player data

`config/steinloader.json`, the HUD layout file, translation keys and mod ids are data players already have.
Add new keys instead of renaming existing ones.
