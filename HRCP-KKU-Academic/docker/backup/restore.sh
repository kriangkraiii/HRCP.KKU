#!/bin/bash
# กู้คืน DB จากไฟล์ backup — รันบน server จากโฟลเดอร์โปรเจค
#
#   ./docker/backup/restore.sh                       # ดูรายการ backup ที่มี
#   ./docker/backup/restore.sh hrcp-20260811-020000.sql.gz
#
# แอปจะถูกหยุดระหว่างกู้คืน แล้วสตาร์ทกลับให้อัตโนมัติ
set -euo pipefail

cd "$(dirname "$0")/../.."

# compose อ่าน .env เองอยู่แล้ว แต่ shell นี้ไม่ได้อ่าน — ต้อง source เพื่อให้
# psql ข้างล่างใช้ user/db ตรงกับที่ตั้งไว้จริง ไม่ใช่ค่า default
[ -f .env ] && set -a && . ./.env && set +a

if [ $# -eq 0 ]; then
    echo "Backup ที่มีอยู่:"
    docker compose run --rm --entrypoint ls backup -lh /backups
    echo
    echo "ใช้: $0 <ชื่อไฟล์>"
    exit 0
fi

FILE="$1"

if ! docker compose run --rm --entrypoint test backup -f "/backups/$FILE"; then
    echo "ERROR: ไม่พบ /backups/$FILE" >&2
    exit 1
fi

echo "!! จะเขียนทับข้อมูลทั้งหมดในฐานข้อมูลด้วย $FILE"
read -r -p "พิมพ์ yes เพื่อยืนยัน: " confirm
[ "$confirm" = "yes" ] || { echo "ยกเลิก"; exit 1; }

echo "--- หยุดแอป (กัน connection เขียนระหว่างกู้คืน) ---"
docker compose stop app

echo "--- กู้คืน ---"
# --clean --if-exists อยู่ในตัว dump ไม่ได้ (dump ไม่ได้ใส่ -c) จึง drop schema เอง
docker compose exec -T db psql -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-hrcp}" \
    -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
docker compose run --rm --entrypoint bash backup -c \
    "gunzip -c '/backups/$FILE' | psql"

echo "--- สตาร์ทแอป ---"
docker compose start app
echo "เสร็จแล้ว"
