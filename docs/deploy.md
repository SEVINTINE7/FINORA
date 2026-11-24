# FINORA — Deployment, backups and rollback (T56)

One Ubuntu 22.04 server runs everything. Files live in `deploy/`.

```
Phone ──HTTPS 443──▶ Nginx (Let's Encrypt) ──▶ 127.0.0.1:8080 API (systemd, user finora) ──▶ 127.0.0.1:3306 MySQL 8
                                                                 nightly 02:30 LK ──▶ /var/backups/finora/*.sql.gz.gpg (30 days, D21)
```

| What | Where on the server | Owner / mode |
|---|---|---|
| API jars | `/opt/finora/releases/api-<UTC stamp>.jar`, `/opt/finora/app.jar` → current | root:finora 640 |
| API secrets (T56.3) | `/etc/finora/finora.env` | finora 400 |
| Backup DB login | `/etc/finora/backup.cnf` | root 400 |
| Backup passphrase | `/etc/finora/backup.pass` (+ offline copy) | root 400 |
| Backups | `/var/backups/finora/` | root 700 |
| Units | `finora-api.service`, `finora-backup.service`, `finora-backup.timer` | |
| Commands | `finora-release`, `finora-rollback`, `finora-backup`, `finora-restore` (in `/usr/local/sbin`) | root |

## 1. First-time server setup (T56.1–T56.3)

Prerequisites: an Ubuntu 22.04 server you can SSH into with a key, and a DNS **A record** for the API domain (e.g. `api.finora.lk`) pointing at the server.

```bash
# on your PC
scp -r deploy you@SERVER:/tmp/finora-deploy
# on the server
cd /tmp/finora-deploy && sudo bash server-setup.sh api.finora.lk you@example.com
```

The script (safe to re-run):
- installs Java 17, MySQL 8, Nginx, certbot, ufw
- creates the system user `finora` (no login shell); the API never runs as root
- firewall: deny all incoming except **22 and 443**. Port 80 is opened only for the few seconds certbot needs (pre/post hooks), then closed again
- MySQL: `bind-address = 127.0.0.1` (`deploy/mysql/99-finora.cnf`) and three users, all limited to the `finora` schema:
  - `finora_app`: SELECT/INSERT/UPDATE/DELETE only (used by the running API)
  - `finora_migrate`: adds CREATE/ALTER/DROP/INDEX/REFERENCES (used by Flyway at start-up, via `SPRING_FLYWAY_USER`)
  - `finora_backup`: SELECT/SHOW VIEW/TRIGGER/LOCK TABLES (used by backups)
- generates random passwords and a JWT secret into `/etc/finora/finora.env` (never printed, never in Git). Template: `deploy/finora.env.example`
- installs the systemd units and enables the nightly backup timer
- gets the Let's Encrypt certificate and enables `deploy/nginx/finora.conf` (TLS 1.2/1.3, HSTS, only `/api/**` and `/actuator/health` are proxied)

After it finishes:
1. **Copy `/etc/finora/backup.pass` to the offline store** (password manager entry "FINORA backup passphrase", lead only). Without it the backups can't be decrypted.
2. When T27/T28 are ready, put the LLM keys in `/etc/finora/finora.env` (`sudo -e /etc/finora/finora.env`), then `sudo systemctl restart finora-api`.

Check:
```bash
sudo ufw status                       # 22/tcp, 443/tcp ALLOW - nothing else
sudo ss -tlnp | grep -E ':3306|:8080' # both on 127.0.0.1 only
sudo ls -l /etc/finora                # finora.env -r-------- finora
```

## 2. Release the API

```bash
# on your PC
cd api && ./mvnw -B verify                     # tests must pass first (CLAUDE.md rule 5)
scp target/api-*.jar you@SERVER:/tmp/api.jar
# on the server
sudo finora-release /tmp/api.jar
```
`finora-release` takes a backup, installs the jar under `releases/`, switches the `app.jar` link, restarts the service and waits up to 90 s for `/actuator/health` = UP. **If the health check fails it switches back to the previous jar by itself.** It keeps the last 5 jars.

Logs: `journalctl -u finora-api -f`

## 3. HTTPS health check from a phone (Done when)

D56 confirms that HTTP is limited to authorized isolated development and testing. During T56 deployment, Sevindu supplies the approved HTTPS origin, a hostname-matching certificate chain trusted by stock Android, and a reachable synthetic test service. The original T56 health and restore checks and T40.1 authenticated release verification remain required.

After the phone health check, Sahan verifies the actual V1 Release APK against the configured HTTPS origin with standard Android TLS validation and synthetic credentials. Retain the release revision, APK SHA-256, successful authentication and original session/privacy evidence in the handoff. Keep production cleartext disabled and certificate and hostname validation enabled.

On a phone using mobile data (not the server's network), open `https://<domain>/actuator/health`.
Expected: padlock shown, body `{"status":"UP"}` (it may also list `groups`). Also check `http://<domain>/` does **not** load (port 80 is closed).

## 4. Backups and restore drill (T56.4, D21)

- `finora-backup.timer` runs every night at 02:30 Asia/Colombo (`systemctl list-timers finora-backup.timer`). It also runs before every release.
- Each run: `mysqldump --single-transaction` → gzip → `gpg --symmetric --cipher-algo AES256`. The script decrypts the result and checks the dump is complete before keeping it.
- Backups older than **30 days** are deleted automatically (D21), so user data removed by T36 is gone from backups within 30 days.
- Run one now: `sudo systemctl start finora-backup && journalctl -u finora-backup -n 5`

**Restore drill.** Do this once after setup and after big schema changes. It restores into a separate database and leaves the live one alone:
```bash
sudo systemctl start finora-backup
latest=$(ls -1t /var/backups/finora/finora-*.sql.gz.gpg | head -n 1)
sudo finora-restore "$latest" finora_restore_drill --compare finora   # must end with RESTORE DRILL PASSED
sudo mysql -e "DROP DATABASE finora_restore_drill"
```
Record the run here:

| Date | Who | Backup file | Result |
|---|---|---|---|
| _not done yet_ | | | |

**Real recovery** (data loss): `sudo systemctl stop finora-api`, then `sudo finora-restore <file> finora --i-know-this-overwrites-production`, then `sudo systemctl start finora-api`.

## 5. Signed release APKs (T56.5)

The keystore is **never** in Git (`*.jks`, `*.keystore` are git-ignored). Create it once, outside the repo:
```bash
keytool -genkeypair -keystore ~/finora-keys/finora-release.jks -alias finora \
        -keyalg RSA -keysize 4096 -validity 10000
```
Keep **two** copies (the lead's PC + the offline store) and store the passwords in the password manager. If it is lost, installed apps can never be updated.

Build (PowerShell example; use `export` in bash):
```powershell
$env:FINORA_KEYSTORE_FILE="C:\Users\<you>\finora-keys\finora-release.jks"
$env:FINORA_KEYSTORE_PASSWORD="..."; $env:FINORA_KEY_ALIAS="finora"; $env:FINORA_KEY_PASSWORD="..."
$env:FINORA_RELEASE_API_URL="https://api.finora.lk/"
cd android; .\gradlew.bat assembleV1Release
```
Output: `android/app/build/outputs/apk/v1/release/app-v1-release.apk`. Check: `apksigner verify --print-certs <apk>`.
If `FINORA_KEYSTORE_FILE` is set but another value is missing (or the API URL is empty), the build stops with a clear message. Without it, release builds stay unsigned (CI and debug are unaffected).

## 6. Rollback (T56.6)

1. **Bad API release, no database change** (most cases):
   `sudo finora-rollback` (goes back to the previous jar), or `sudo finora-rollback /opt/finora/releases/api-<stamp>.jar` for an older one. It restarts the API and checks health.
2. **The bad release ran a Flyway migration.** Migrations only go forward. The old jar still starts if the change only *added* tables/columns (Flyway ignores newer migrations it doesn't know about; Hibernate only checks the columns it uses). If the old jar fails to start or data is wrong:
   1. `sudo systemctl stop finora-api`
   2. Restore the backup that `finora-release` took just before the release (the newest file older than the release time in `/var/backups/finora/`):
      `sudo finora-restore <file> finora --i-know-this-overwrites-production`
   3. `sudo finora-rollback <older jar>`
   Data written between that backup and the restore is lost, so announce it to users.
3. **Bad Android release:** APKs are installed by hand (no store), so re-share the previous signed APK with a **higher `versionCode`** (Android refuses lower versions). Keep every released APK in the team drive.
4. **Broken certificate/Nginx config:** `sudo nginx -t` shows the error. Restore the file from `deploy/nginx/finora.conf`, then `sudo systemctl reload nginx`. Certificate problems: `sudo certbot renew --dry-run`.
### History recovery activation (D43)

Set `FINORA_HISTORY_ACTIVATION_INSTANT` to the approved production activation timestamp in ISO-8601 UTC on first activation. The scheduler persists the first full Asia/Colombo month at or after that instant. Subsequent starts use the stored cutoff, and a conflicting configured cutoff stops the recovery pass. With neither a configured activation nor a stored cutoff, only the ordinary D27 jobs run. Review any gaps in the reported ranges outside the automatic six-closed-month window before arranging owner-directed recovery. The worker schedules missing older snapshots separately from D27's previous-month and dirty work.

### First ADMIN provisioning (D49)

On a new deployment, set `FINORA_BOOTSTRAP_ADMIN_EMAIL` and `FINORA_BOOTSTRAP_ADMIN_PASSWORD` through the deployment secret store before starting the service. Use an authorized address and a unique high-entropy password of at least 16 characters and at most 72 UTF-8 bytes. When both variables are absent the bootstrap is inactive. The application creates one ADMIN only if none exists, including when multiple instances start together. It never resets an existing password. Remove the bootstrap variables once the administrator can sign in. Share subsequent STAFF activation credentials only through the approved secure channel.
