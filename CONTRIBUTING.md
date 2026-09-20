# Contributing to Snatter

Thanks for your interest in Snatter. The project is early, so the best way to
contribute right now is to open an issue and talk to us before writing code.

## Developer Certificate of Origin

Snatter uses the [Developer Certificate of Origin](DCO) (DCO) instead of a
contributor license agreement. By signing off on a commit you certify that you
wrote the code or otherwise have the right to submit it under the project's
license.

Sign off every commit with the `-s` flag:

```
git commit -s -m "Add something useful"
```

This appends a line like `Signed-off-by: Your Name <you@example.com>` using
the name and email from your git configuration. Pull requests with unsigned
commits will not be merged.

## Licensing of contributions

- Code under `server/` and `client/` is licensed under the AGPL-3.0.
- Code under `protocol/` (and any future SDKs) is licensed under Apache-2.0.

By contributing you agree that your contribution is licensed under the license
that applies to the directory it lands in.

## Toolchain

- Server: JDK 25 or newer and Maven. [SDKMAN](https://sdkman.io) is the easiest way to
  get both.
- Client: Node.js LTS.
