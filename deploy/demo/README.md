# The demo at demo.snatter.app

demo.snatter.app runs the newest release of Snatter, the server with the
web app in front of it, for anyone to try. This directory is what runs
there: the files in `/opt/snatter` on the demo host are copies of these.

## How a release gets there

1. A version tag starts the Release workflow (see "Releases" in
   `CONTRIBUTING.md`), which publishes the images.
2. When it succeeds, the Deploy demo workflow
   (`.github/workflows/deploy-demo.yml`) starts by itself, if the release is
   not a prerelease and is the newest one. Otherwise it says so in its run
   and does nothing.
3. It logs in to the host over SSH as `snatter-deploy`, whose key may run
   nothing but `deploy.sh`, and hands it the version.
4. `deploy.sh` writes the version into `.env`, pulls the images and
   restarts what changed, waiting until the containers are up.
5. The workflow then checks that `https://demo.snatter.app/api/v1/server-info`
   reports the version, and fails if it does not within two minutes.

Nothing else changes what runs on the host: there is no timer following the
images of `main`.

## Deploying by hand

A prerelease, a rollback to an earlier release, or the same release again:

```
gh workflow run deploy-demo.yml -f version=0.1.0-rc.1
```

or Actions → Deploy demo → Run workflow in GitHub. The version is any
release whose images exist.

Without GitHub, on the host itself:

```
ssh root@demo.snatter.app
sudo -u snatter-deploy /opt/snatter/deploy.sh 0.1.0-rc.1
```

## Looking at it

On the host, in `/opt/snatter`:

| What                         | How                                                  |
|------------------------------|------------------------------------------------------|
| The version running          | `grep SNATTER_VERSION .env`                          |
| The containers               | `docker compose ps`                                  |
| Logs, following them         | `docker compose logs -f server` (or `web`, `caddy`)  |
| Logs of earlier containers   | `journalctl -t snatter-server-1 --since today`       |
| From outside                 | `curl https://demo.snatter.app/api/v1/server-info`   |

The logs are kept 30 days, across deploys.

## What is on the host

An Ubuntu machine with Docker and the compose plugin.

| Where                                         | What                                                          |
|-----------------------------------------------|---------------------------------------------------------------|
| `/opt/snatter/compose.yaml`                   | `compose.yaml` here: the server, the web app and Caddy        |
| `/opt/snatter/Caddyfile`                      | `Caddyfile` here: HTTPS from Let's Encrypt, `/api` to the server |
| `/opt/snatter/deploy.sh`                      | `deploy.sh` here, the only way to change what runs            |
| `/opt/snatter/.env`                           | `SNATTER_DOMAIN`, `SNATTER_MEDIA_ADDRESS`, and `SNATTER_VERSION` from `deploy.sh` |
| `/etc/systemd/journald.conf.d/20-snatter.conf` | `journald-snatter.conf` here: logs kept 30 days              |
| `/etc/apt/apt.conf.d/52auto-reboot`           | `auto-reboot.conf` here: rebooting at 04:00 after updates that need it |
| Volumes `snatter_server-data`, `snatter_caddy-data`, `snatter_caddy-config` | The database and uploads; Caddy's certificates |
| User `snatter-deploy`                         | Owns `/opt/snatter`, may use Docker; its key in `~/.ssh/authorized_keys` runs only `deploy.sh` |

Membership of the `docker` group is as good as root on the host; the
key's restriction is what limits the deploy user.

## Changing the host's setup

Change the files here, merge, and copy them over, then deploy the running
version again so the containers take the change:

```
scp deploy/demo/compose.yaml deploy/demo/Caddyfile deploy/demo/deploy.sh root@demo.snatter.app:/opt/snatter/
ssh root@demo.snatter.app 'chown snatter-deploy: /opt/snatter/* && cd /opt/snatter && sudo -u snatter-deploy ./deploy.sh "$(sed -n "s/^SNATTER_VERSION=//p" .env)"'
```

The system files go to the places in the table above, followed by
`systemctl restart systemd-journald` for the journal.

## Setting up a host from scratch

1. Install Docker with the compose plugin, and unattended upgrades.
2. Point `demo.snatter.app` at the machine, and open TCP 80 and 443, UDP 443
   (HTTP/3) and UDP 8080 (voice).
3. Copy `compose.yaml`, `Caddyfile` and `deploy.sh` into `/opt/snatter`, and
   write `/opt/snatter/.env`:

   ```
   SNATTER_DOMAIN=demo.snatter.app
   SNATTER_MEDIA_ADDRESS=<the machine's public IP address>
   ```

4. Install `journald-snatter.conf` and `auto-reboot.conf` where the table
   above says.
5. Make the deploy user, owning the directory:

   ```
   useradd --create-home --groups docker snatter-deploy
   chown -R snatter-deploy: /opt/snatter
   ```

6. Make a key pair for the workflow, away from the host:

   ```
   ssh-keygen -t ed25519 -N '' -C deploy-demo -f deploy-demo
   ```

   and give the host the public key, limited to `deploy.sh`, in
   `~snatter-deploy/.ssh/authorized_keys`:

   ```
   restrict,command="/opt/snatter/deploy.sh" ssh-ed25519 AAAA... deploy-demo
   ```

7. In the repository settings, the `demo` environment, deployable only from
   `main`, holds:
   - the secret `DEMO_SSH_KEY`: the private key, `deploy-demo`, which can
     then be deleted
   - the variable `DEMO_SSH_HOST`: `demo.snatter.app`
   - the variable `DEMO_SSH_USER`: `snatter-deploy`
   - the variable `DEMO_SSH_KNOWN_HOSTS`: the output of
     `ssh-keyscan -t ed25519 demo.snatter.app`, checked against
     `/etc/ssh/ssh_host_ed25519_key.pub` on the host
8. Deploy the newest release by hand, as above. The first account created
   becomes the owner.

A new host has a new host key: update `DEMO_SSH_KNOWN_HOSTS`.

## When a deploy goes wrong

- **The workflow cannot log in**: the key in `DEMO_SSH_KEY`, the line in
  `~snatter-deploy/.ssh/authorized_keys`, or `DEMO_SSH_KNOWN_HOSTS`, as after
  the host was rebuilt.
- **`deploy.sh` fails**: its output is in the workflow's Deploy step. A
  version without images fails at the pull and leaves the old one running.
- **The demo reports another version**: the containers started but the
  server did not come up in time, or failed; see the logs. Deploy the
  previous release to roll back. That does not undo a database migration the
  newer release ran, which an older server may not cope with; the
  `snatter_server-data` volume has no backups yet, so on a demo the way out
  is a fresh volume (`docker compose down`, `docker volume rm
  snatter_server-data`, deploy again), which starts the demo over.
- **Nothing was deployed after a release**: it was a prerelease, or not the
  newest release; the Deploy demo run says which. Deploy it by hand.
