# Deployment host units

The scheduled work on the deployment host: three systemd units and the scripts they run.
Nothing in the pipeline installs these — they live on the host, and until this directory
existed they lived *only* there, so a rebuilt or replaced instance lost them silently.

| Unit | Schedule | Script |
| --- | --- | --- |
| `pg-backup.timer` | Daily, randomised up to 15 min | `pg-backup.sh` |
| `docker-image-gc.timer` | Weekly (Monday 00:00 UTC), randomised up to 1 h | `docker-image-gc.sh` |
| `app-server-netfix.service` | At boot | `app-server-netfix.sh` |

`docs/deployment.md` explains what each one is for. The files here are the copies that were
running on 2026-09-06; treat the host as the thing to update *from* this directory, not the
other way round.

## Installing on a fresh host

```bash
sudo install -m 0755 -o root -g root pg-backup.sh docker-image-gc.sh app-server-netfix.sh /usr/local/bin/
sudo install -m 0644 -o root -g root ./*.service ./*.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now pg-backup.timer docker-image-gc.timer
sudo systemctl enable app-server-netfix.service        # boot-only, nothing to start now
```

The shell scripts are pinned to LF in `.gitattributes`. Copied to the host from a Windows
checkout without that, the shebang becomes `#!/usr/bin/env bash` followed by a carriage
return, and the host answers with a "no such file or directory" that names a file which
plainly exists.

## Checking they work

```bash
systemctl list-timers pg-backup.timer docker-image-gc.timer --no-pager
sudo systemctl start pg-backup.service && sudo ls -lt /var/backups/postgres | head -3
sudo KEEP_HOURS=72 /usr/local/bin/docker-image-gc.sh
journalctl -u app-server-netfix.service -b 0 --no-pager
```

`docker-image-gc.sh` reads `KEEP_HOURS` from the environment (72 by default), so a one-off
run can use a different window without editing the unit.
