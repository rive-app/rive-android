# Android Release Script

## Release Branch Policy

Major and minor releases must run from the repository's default branch, currently `master`.
Maintenance branches can only create patch releases.

## Passing the Target Version to `release-it`

The Android release workflow reads the current version from the `VERSION` file into the
`current_version` shell variable. After validating that value as stable SemVer, it separates the
major, minor, and patch components and applies the bump selected in the GitHub Actions UI. The
result becomes the `target_version` shell variable. For example:

```text
VERSION file: 12.0.0 + selected bump: Patch → target_version: 12.0.1
```

The validation step writes `target_version` to its runner-provided `$GITHUB_OUTPUT` file. This is
a line-oriented file using a simple `name=value` format, so the workflow appends a line such as
`target_version=12.0.1`. After the step completes, GitHub Actions parses the file and exposes the
value to later steps as `steps.release.outputs.target_version`. The release step maps that output
to its `TARGET_VERSION` environment variable, producing this lineage:

```text
VERSION → current_version → target_version → step output → TARGET_VERSION → release-it
```

Mapping the step output through an environment variable keeps the shell script static while
passing the calculated version as data. A direct expression inside `run` would be substituted into
the script before the shell parses it; an environment variable expanded as `"$TARGET_VERSION"`
remains a single argument even if its value contains shell metacharacters. The SemVer validation
already constrains this value, so this is defense in depth and a consistent boundary for passing
dynamic values to shell commands.

The package script maps `npm run release` to `release-it`. The first `--` below tells npm to pass
the remaining arguments to that script:

```sh
npm run release -- "$TARGET_VERSION" --ci
```

For `TARGET_VERSION=12.0.1`, this effectively runs `release-it 12.0.1 --ci`. The positional
`12.0.1` becomes release-it's target `version`; `--ci` disables interactive prompts.

## Current and Target Versions

The release step passes two workflow outputs through its environment:

- `$CURRENT_VERSION` is the version read from the Android `VERSION` file before it is bumped. It is
  the current completed release, such as `12.0.0`.
- `$TARGET_VERSION` is the version calculated by the workflow and passed to `release-it`, such as
  `12.0.1`.

These are workflow environment variables rather than release-it template variables. Release-it
generates `git.changelog` before it adds its `latestVersion` and `version` values to the global
template context, so those template variables are not available when this command is rendered.

The `@release-it/bumper` plugin's `in` configuration makes the Android `VERSION` file the source
of release-it's current version. Its `out` configuration writes release-it's target version back to
that file during the bump. The plugin does not choose the target version; the workflow passes that
version as the positional argument. Without this plugin, release-it would normally read the current
version from `package.json` or the latest Git tag and persist the target through its corresponding
version mechanism.

## Release Notes and `CHANGELOG.md`

The two `auto-changelog` commands serve different phases of the release:

1. `git.changelog` runs before the bump and writes release-note text to standard output. The
   `--unreleased --latest-version $CURRENT_VERSION` arguments mean "collect changes after the
   selected branch's current completed release through `HEAD`." Pinning `CURRENT_VERSION` prevents a
   newer tag from another release line, such as `12.1.0`, from becoming the boundary for a
   `12.0.1` maintenance release.
2. The `after:bump` hook regenerates and stages `CHANGELOG.md`. At this point `$TARGET_VERSION` is
   the new target, so `--latest-version $TARGET_VERSION` assigns those formerly unreleased changes
   to the new `12.0.1` section.

For a `12.0.1` maintenance release, the lifecycle is therefore:

```text
VERSION contains 12.0.0
    → CURRENT_VERSION = 12.0.0
    → collect commits after 12.0.0 as unreleased release notes
    → bump to TARGET_VERSION = 12.0.1
    → write those commits under 12.0.1 in CHANGELOG.md
```
