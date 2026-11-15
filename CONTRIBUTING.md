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
commits will not be merged; a check on each pull request lists them. To sign
off commits you already made, run `git rebase --signoff main` and force-push.

The one exception is Dependabot, which cannot sign off its commits. Its pull
requests only bump versions of third-party dependencies, so they may be merged
without a sign-off.

## Licensing of contributions

- Code under `server/` and `client/` is licensed under the AGPL-3.0.
- Code under `protocol/` (and any future SDKs) is licensed under Apache-2.0.

By contributing you agree that your contribution is licensed under the license
that applies to the directory it lands in.

## Toolchain

- Server: JDK 25 or newer and Maven. [SDKMAN](https://sdkman.io) is the easiest way to
  get both.
- Client: Node.js 24 or newer.

## Releases

Every commit to `main` is published as a development build: images tagged
`main` and `sha-<commit>`. A release is a version tag on a commit of `main`
that CI passed on, following [semantic versioning](https://semver.org). A
tag with a suffix, such as `v0.2.0-rc.1`, is a prerelease.

### Cutting a release

1. Pick the commit: the head of `main`, normally a merge, with a green
   "CI result" on its push run. A commit inside a pull request has no such
   run and is refused.
2. Tag it and push the tag, one tag at a time:

   ```
   git switch main && git pull
   git tag -a v0.1.0 -m "Snatter 0.1.0"
   git push origin v0.1.0
   ```

3. Follow the Release workflow (`gh run watch`, or Actions in GitHub). It
   checks the tag, waits for CI if it is still running, builds the images
   for linux/amd64 and linux/arm64 with the tag's version, tries them, and
   publishes them, then makes the GitHub release with notes from the pull
   requests merged since the last one.
4. A stable release goes on the demo by itself (Deploy demo); a
   prerelease, by hand. Check `https://demo.snatter.app/api/v1/server-info`
   reports the version. The demo's runbook is `deploy/demo/README.md`.
5. Move `revision` in `server/pom.xml` on to the next version being worked
   towards, such as `0.2.0-SNAPSHOT`, in a pull request.

### What a release publishes

| What                     | Stable release, such as `v0.1.0`              | Prerelease, such as `v0.2.0-rc.1` |
|--------------------------|-----------------------------------------------|-----------------------------------|
| Image tags               | `0.1.0`; `0.1` and `latest` if it is the newest of them; `1` too from 1.0 on | `0.2.0-rc.1` only |
| GitHub release           | Marked Latest if it is the newest             | Marked as a prerelease            |
| demo.snatter.app         | Deployed, if it is the newest                 | By hand                           |

The moving tags follow the newest release only, so a fix for an older line,
`v0.1.1` after `v0.2.0`, gets its own tag and leaves `latest` and the demo
alone.

### When a release goes wrong

- **"is not on main" or "CI never ran"**: the tag is on the wrong commit.
  Delete it (`git push origin :v0.1.0` and `git tag -d v0.1.0`) and tag the
  right one.
- **"CI ... ended in failure"**: fix `main`, then tag the fixed commit with a
  new version; versions are not reused once images are out.
- **It failed after the images were pushed**: run the workflow again
  (`gh run rerun <id> --failed`); every step may run twice.
- **Two tags pushed at once**: releases run one at a time, and a run
  waiting behind another is replaced by any that comes after it. Run the
  replaced one again from its tag.

The server reports the version it was built with in `server-info`;
`revision` in `server/pom.xml` is only what development builds report. The
protocol version is separate (`protocol/README.md`).
