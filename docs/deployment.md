# Deployment

ทั้งระบบรันด้วย Docker Compose 3 container บน server เดียว

| Container | Image | หน้าที่ |
|---|---|---|
| `app` | `hrcp-academic:latest` (build เอง) | Spring Boot + LibreOffice + ฟอนต์ TH Sarabun |
| `db` | `postgres:16` | ฐานข้อมูล |
| `backup` | `postgres:16` | `pg_dump` ทุกวันตามเวลาที่ตั้ง |

Volume: `pgdata` (ข้อมูล DB), `uploads` (ไฟล์แนบ), `backups` (ไฟล์ dump)

`db` ไม่ publish port ออก host — เข้าถึงได้เฉพาะ container ใน network เดียวกัน

## ตั้ง server ใหม่

ทำตามลำดับนี้แล้ว **reboot ครั้งเดียวตอนท้ายข้อ 4**

1. `apt update && apt full-upgrade -y`
2. ติดตั้ง Docker Engine + compose plugin
3. `usermod -aG docker project`
4. `timedatectl set-timezone Asia/Bangkok` → **reboot**
5. clone/รับโค้ดลง server, สร้าง `.env`:
   ```bash
   cd HRCP-KKU-Academic
   cp .env.example .env && chmod 600 .env
   openssl rand -base64 32          # ใส่เป็น POSTGRES_PASSWORD
   ```
6. จากเครื่อง dev: `./deploy.sh` (ส่ง image `postgres:16` ให้อัตโนมัติในครั้งแรก)
7. `docker compose up -d`

ไม่ต้องติดตั้ง PostgreSQL, Java, LibreOffice หรือฟอนต์บนตัว server เลย

## Deploy โค้ดใหม่

```bash
./deploy.sh
```

build image → `docker save | ssh docker load` → `git push production main` แล้ว post-receive hook
สั่ง restart

`db` กับ `backup` ใช้ image `postgres:16` ที่ไม่เปลี่ยน จึง**ไม่ถูก recreate** — ฐานข้อมูลรันต่อเนื่อง
ไม่ restart ตอน deploy

## ข้อมูลหายเมื่อไหร่

ข้อมูลอยู่ใน named volume `pgdata` ไม่ได้อยู่ใน image — `docker build` / `docker load` /
`docker compose up` / reboot **ไม่กระทบ**

คำสั่งที่ลบข้อมูลจริง (ต้องตั้งใจพิมพ์):

- `docker compose down -v` ← `-v` คือตัวอันตราย, `down` เฉย ๆ ปลอดภัย
- `docker volume rm hrcp-kku-academic_pgdata`
- `docker volume prune`

## Backup

Container `backup` dump ทุกวันเวลา `BACKUP_AT` (ค่าเริ่มต้น 02:00 เวลาไทย) เก็บไว้
`BACKUP_KEEP_DAYS` วัน (ค่าเริ่มต้น 14) ตั้งค่าใน `.env`

ไฟล์ dump เขียนเป็น `.tmp` ก่อนแล้วค่อย `mv` — ถ้า dump พังกลางคันจะไม่เหลือไฟล์ครึ่ง ๆ
ที่ดูเหมือนใช้ได้

```bash
docker compose logs -f backup                                    # ดู log / เวลารอบถัดไป
docker compose run --rm --entrypoint ls backup -lh /backups      # ดูไฟล์ที่มี
./docker/backup/restore.sh                                       # ดูรายการ backup
./docker/backup/restore.sh hrcp-20260811-020000.sql.gz           # กู้คืน (หยุดแอปให้เอง)
```

**Volume ไม่ใช่ backup** — มันกัน deploy ไม่ให้ข้อมูลหาย แต่ไม่กันดิสก์พังหรือลบผิด
ควรก๊อปไฟล์จาก volume `backups` ออกไปเก็บนอกเครื่องเป็นระยะ:

```bash
docker run --rm -v hrcp-kku-academic_backups:/b -v "$PWD:/out" alpine \
    sh -c 'cp /b/$(ls -t /b | head -1) /out/'
```

## ทำไม DB ไม่อยู่ใน image เดียวกับแอป

Container ออกแบบมาให้ 1 process ถ้ารวมกันต้องใช้ `supervisord` ครอบ ซึ่งทำให้ signal จาก
`docker stop` ไปไม่ถึง Postgres ตรง ๆ เสี่ยง data corruption และทุกครั้งที่ deploy โค้ดใหม่
DB จะถูก restart ไปด้วยโดยไม่จำเป็น แยก container ได้ข้อดีเรื่อง "ตั้ง server ใหม่ง่าย"
เหมือนกันโดยไม่ต้องแลกอะไร
