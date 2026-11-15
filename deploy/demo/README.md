# The demo host

demo.snatter.app runs the latest release. The Deploy demo workflow
(`.github/workflows/deploy-demo.yml`) puts each release there over SSH, with
a key that may run nothing but `deploy.sh`.

## Setting the host up

Once per host. Docker with the compose plugin, and a reverse proxy that
terminates TLS in front of the web container, are taken as given.

1. Copy `compose.yaml` and `deploy.sh` into a directory of their own, such as
   `/opt/snatter`, and give it a `.env` with at least `SNATTER_MEDIA_ADDRESS`,
   the host's public address. Keep the volume name the host has used so far,
   so that the data stays.
2. Make a user for deployments that may use Docker, owning that directory:

   ```
   useradd --create-home --groups docker snatter-deploy
   chown -R snatter-deploy: /opt/snatter
   ```

   Membership of the docker group is as good as root on the host; the key
   restriction below is what limits it.
3. Make a key pair for the workflow, away from the host:

   ```
   ssh-keygen -t ed25519 -N '' -C deploy-demo -f deploy-demo
   ```

   Give the host the public key, limited to `deploy.sh`, in
   `~snatter-deploy/.ssh/authorized_keys`:

   ```
   restrict,command="/opt/snatter/deploy.sh" ssh-ed25519 AAAA... deploy-demo
   ```
4. In the repository settings, make an environment called `demo`, deployable
   only from `main`, with:
   - the secret `DEMO_SSH_KEY`: the private key, `deploy-demo`, which can then
     be deleted
   - the variable `DEMO_SSH_HOST`: `demo.snatter.app`
   - the variable `DEMO_SSH_USER`: `snatter-deploy`
   - the variable `DEMO_SSH_KNOWN_HOSTS`: the output of
     `ssh-keyscan -t ed25519 demo.snatter.app`, having checked its key against
     `/etc/ssh/ssh_host_ed25519_key.pub` on the host
5. Stop whatever updated the demo before, such as a container that follows
   the `main` images, or it will undo the deployments.
6. Deploy the first time with Run workflow on Deploy demo, naming a released
   version.
