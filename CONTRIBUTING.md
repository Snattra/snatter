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
4. A stable release goes on the demo and web.snatter.app by itself; a
   prerelease, by hand (see "The demo" and "The web app" below). Check that
   `https://demo.snatter.app/api/v1/server-info` reports the version, and that
   web.snatter.app's page carries it in `<meta name="snatter-version">`.
5. Move `revision` in `server/pom.xml` on to the next version being worked
   towards, such as `0.2.0-SNAPSHOT`, in a pull request.

### What a release publishes

| What                     | Stable release, such as `v0.1.0`              | Prerelease, such as `v0.2.0-rc.1` |
|--------------------------|-----------------------------------------------|-----------------------------------|
| Image tags               | `0.1.0`; `0.1` and `latest` if it is the newest of them; `1` too from 1.0 on | `0.2.0-rc.1` only |
| GitHub release           | Marked Latest if it is the newest             | Marked as a prerelease            |
| demo.snatter.app         | Deployed, if it is the newest                 | By hand                           |
| web.snatter.app          | Deployed, if it is the newest                 | By hand                           |
| `snatter-web-<version>.zip` | Attached to the GitHub release             | Attached too                      |

The moving tags follow the newest release only, so a fix for an older line,
`v0.1.1` after `v0.2.0`, gets its own tag and leaves `latest` and the demo
alone.

### When a release goes wrong

- **"is not on main" or "CI never ran"**: the tag is on the wrong commit.
  Delete it (`git push origin :v0.1.0` and `git tag -d v0.1.0`) and tag the
  right one.
- **"CI ... ended in failure"**: fix `main`, then tag the fixed commit with a
  new version; versions are not reused once images are out.
- **It failed after the images were pushed**: run the whole workflow again
  (`gh run rerun <id>`), not only the failed jobs: the moving tags are then
  chosen again, against the releases there are by then. Every step may run
  twice.
- **Two tags pushed at once**: releases run one at a time, and a run
  waiting behind another is replaced by any that comes after it. Run the
  replaced one again from its tag.

The server reports the version it was built with in `server-info`;
`revision` in `server/pom.xml` is only what development builds report. The
protocol version is separate (`protocol/README.md`).

### The demo

demo.snatter.app runs a release of the server, with Caddy in front for
HTTPS; its address redirects to web.snatter.app, the app people use with
it, and so do its invite links, naming the server
(`/invite#{server}/{code}`). `deploy/demo` holds its compose file, Caddyfile
and system settings, and `deploy.sh`, the one way to change what runs there.

Once a release succeeds, the Deploy demo workflow
(`.github/workflows/deploy-demo.yml`) puts it there by itself, if it is not a
prerelease and is the newest release; otherwise its run says why not. It
logs in to the host over SSH as `snatter-deploy`, whose key may run nothing
but `deploy.sh`, and hands it the version. `deploy.sh` pulls that version's
images, records the version and restarts what changed, then reloads Caddy;
the workflow checks that `server-info` reports the version.

To deploy a prerelease, roll back to an earlier release, or deploy again:

```
gh workflow run deploy-demo.yml -f version=0.1.0-rc.1
```

The key and the host's address live in the `demo` environment of the
repository settings, deployable only from `main`. Everything about the
host itself, from reading its logs to changing its setup or building a new
one, is in its runbook, `/opt/snatter/README.md` on the host.

### The web app

web.snatter.app serves the hosted web app, the multi-server build that
anyone can use with any server: `npm run build:hosted` in `client/`, which
ships its security headers in a `_headers` file (`client/web/hosted`). Each
release attaches that build as `snatter-web-<version>.zip`, for anyone to
host, and the same zip is what web.snatter.app serves.

It is a Cloudflare Pages project, `snatter-web`. Once a release succeeds,
the Deploy web workflow (`.github/workflows/deploy-web.yml`) uploads the
release's zip there by itself, on the same terms as the demo: not
prereleases, and only the newest release. It then checks that the page
carries the version. To deploy a prerelease, roll back, or deploy again:

```
gh workflow run deploy-web.yml -f version=0.1.0-rc.2
```

The `web` environment of the repository settings, deployable only from
`main`, holds the Cloudflare API token (`CLOUDFLARE_API_TOKEN`) and the
account's id (`CLOUDFLARE_ACCOUNT_ID`). Cloudflare also keeps every
deployment, so one can be rolled back to from the project's Deployments in
its dashboard as well.

How the project is set up, should it need doing again:

1. In the Cloudflare dashboard, Workers & Pages → Create → **Pages** →
   Upload assets, named `snatter-web`. It must be a Pages project: a
   Worker's address ends in `.workers.dev` rather than `.pages.dev`, and a
   Worker's custom domain needs the whole domain on Cloudflare. Its
   production branch, the one its domains serve, is set as it is made and
   shown nowhere in the dashboard; the workflow asks Cloudflare for it and
   deploys there, as a deploy to any other branch is only a preview.
2. In the project, Custom domains → Set up a custom domain →
   `web.snatter.app`, and only then the CNAME record at the DNS provider:
   `web` → `snatter-web.pages.dev`. A record pointing at a project that
   does not know the domain fails. The domain is not added under the
   account's Domains, which is for moving a whole domain to Cloudflare.
3. An API token (My Profile → API Tokens → Create Custom Token) with
   Account → Cloudflare Pages → Edit, for this account only, and nothing
   else, set as the `web` environment's secret. Replace it the same way
   when it expires or might have leaked, and delete the old one.
