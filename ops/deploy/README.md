# PrintFlow VPS Deploy (Systemd + Nginx)

Ovaj folder sadrzi production-ready skeleton za deploy bez Docker-a.

## 1) Build artifact

Na lokalnoj masini ili CI:

```bash
./mvnw -DskipTests clean package
```

Artifact: `target/printflow-saas-*.jar`

## 2) Server prerequisites

- Ubuntu 22.04+
- Java 21
- MySQL 8+
- Nginx
- korisnik `printflow`

Primer:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless nginx mysql-client
sudo useradd --system --home /opt/printflow --shell /usr/sbin/nologin printflow || true
sudo mkdir -p /opt/printflow/{app,logs,uploads}
sudo chown -R printflow:printflow /opt/printflow
```

## 3) App files

Kopiraj jar u:

- `/opt/printflow/app/printflow.jar`

Kopiraj env fajl:

- `/etc/printflow/printflow.env`

Template env: `ops/deploy/printflow.env.example`.

Brza opcija deploy-a (na samom VPS-u iz repo-a):

```bash
./ops/deploy/deploy-vps.sh
```

## 4) Systemd service

Kopiraj `ops/deploy/printflow.service` u:

- `/etc/systemd/system/printflow.service`

Zatim:

```bash
sudo systemctl daemon-reload
sudo systemctl enable printflow
sudo systemctl restart printflow
sudo systemctl status printflow --no-pager
```

## 5) Nginx reverse proxy

Kopiraj `ops/deploy/nginx-printflow.conf` u:

- `/etc/nginx/sites-available/printflow.conf`

Aktiviraj:

```bash
sudo ln -sf /etc/nginx/sites-available/printflow.conf /etc/nginx/sites-enabled/printflow.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 6) TLS (Let's Encrypt)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com -d www.your-domain.com
```

## 7) Smoke checks

```bash
curl -I http://127.0.0.1:8088/public/
curl -I https://your-domain.com/public/
```

Proveri:

- `/admin/dashboard`
- `/public/track`
- pricing kalkulator add/remove item
- quote pdf download

## 8) Rollback

Drzi prethodni jar kao:

- `/opt/printflow/app/printflow.prev.jar`

Rollback:

```bash
sudo systemctl stop printflow
sudo cp /opt/printflow/app/printflow.prev.jar /opt/printflow/app/printflow.jar
sudo systemctl start printflow
```
