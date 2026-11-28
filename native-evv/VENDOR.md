# The engine, vendored

`native/openevv` is the ECI engine (Eloquent/Eloquence) as published by
[openevv](https://github.com/Mudb0y/openevv), copied into this repository
rather than fetched as a git submodule. It is here at commit
`9b5b801fa618b727669d8cf40ddf02af8a21d826` ("Merge Storm Dragon's Speech
Dispatcher module (#14)") with no `.git` of its own, so a clone of this
repository is a complete source tree: nothing has to be initialised, nothing
has to be fetched, and a build never depends on a second host being reachable.

## What was kept

Everything the Android build reads:

| Path | Why |
| --- | --- |
| `Makefile`, `include/`, `lib/`, `src/` | the engine and the ECI layer |
| `lang/enus`, `lang/engb`, `lang/dede`, `lang/eses`, `lang/esus`, `lang/frfr`, `lang/frca`, `lang/itit` | the eight modules this app speaks, each with the rule text `tools/rules/notation.py` compiles |
| `tools/` | the rule compiler, the decompiler and the module generators the Makefile calls |
| `cli/` | the engine's own front end, so the tree still builds on its own |
| `docs/`, `reference/`, `README.md`, `NOTICE`, `LICENSE` | the engine's documentation and the licences it is distributed under |

## What was left out

Only the parts this app never builds, and each for a reason:

* `lang/jajp`, `rom/jajp` -- Japanese is not one of the eight languages this
  app offers, and it is the one module that needs a separate romanizer.
* `lang/plpl` -- the Polish module is unfinished upstream.
* `test/` -- eleven megabytes of the engine's own harness, cases and sample
  waves. It drives `build/probe` on a desktop; nothing here can run it, and
  the app's own instrumented tests are the gate for this repository.
* `nvda/`, `speechd/`, `win/` -- bindings and a porting layer for hosts this
  app is not.
* `flake.nix`, `flake.lock`, `.github/`, `CLAUDE.md` -- the upstream build's
  own tooling and continuous integration.

## What was changed in it

One patch, applied to the source rather than carried as a file under
`native/patches`. It is the only change to upstream, and it is written into
`src/eci/sound/eci_pcm.c`:

> `evv_setUpsampleMethod()` and `evv_upsample_override` add a runtime choice of
> the upsampling method. Upstream picks it once per process out of
> `EVV_UPSAMPLE`, which is an experiment knob and not a setting. The Android
> front end offers it as one -- see Audio Optimization -- so it needs a call:
> the override is read every time a converter is (re)built, and re-setting the
> sample rate after the call rebuilds the converter and puts the new method in
> force at once. Negative (the default) keeps the old behaviour exactly.

Applying the patch in place rather than patching at build time is what makes a
build reproducible from the tree alone: there is no "already applied" state to
detect, no write to a checked-in file during a build, and no way for two builds
started at once to race over the same sources.

## Updating it

Copy the new upstream tree over this one, keep the list above, and re-apply the
patch to `src/eci/sound/eci_pcm.c` by hand: the override has to be in
`cvt_method()` before the environment is read, and the two functions have to be
beside `pcm_cvt_bytes`.
