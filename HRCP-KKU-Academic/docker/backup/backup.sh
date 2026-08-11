#!/bin/bash
# pg_dump ของ hrcp ทุกวันเวลา $BACKUP_AT (Asia/Bangkok) แล้วลบไฟล์ที่เก่ากว่า
# $BACKUP_KEEP_DAYS วัน
#
# ใช้ sleep-until-next-run แทน cron เพราะ image postgres ไม่มี cron ติดมา และ
# cron ไม่ inherit env ของ container (PGPASSWORD, PGHOST) ต้องเขียน env ลงไฟล์
# เพิ่มอีกชั้น — loop ตรงนี้อ่านง่ายกว่าและ log ออก stdout ให้ docker logs เห็นเลย
set -uo pipefail

BACKUP_DIR=/backups
BACKUP_AT="${BACKUP_AT:-02:00}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"

log() { echo "[backup] $(date '+%Y-%m-%d %H:%M:%S') $*"; }

if ! [[ "$BACKUP_AT" =~ ^([01][0-9]|2[0-3]):[0-5][0-9]$ ]]; then
    log "ERROR: BACKUP_AT ต้องเป็นรูปแบบ HH:MM (24 ชม.) แต่ได้ '$BACKUP_AT'"
    exit 1
fi

run_backup() {
    local stamp file
    stamp=$(date '+%Y%m%d-%H%M%S')
    file="$BACKUP_DIR/hrcp-$stamp.sql.gz"

    log "เริ่ม dump -> $file"
    # เขียนลง .tmp ก่อน แล้วค่อย mv — ถ้า dump พังกลางคัน จะไม่เหลือไฟล์
    # ขนาดครึ่ง ๆ ที่ดูเหมือน backup ที่ใช้ได้
    if pg_dump --no-owner --no-privileges | gzip -c > "$file.tmp"; then
        mv "$file.tmp" "$file"
        log "สำเร็จ ($(du -h "$file" | cut -f1))"
    else
        rm -f "$file.tmp"
        log "ERROR: dump ล้มเหลว"
        return 1
    fi

    local removed
    removed=$(find "$BACKUP_DIR" -name 'hrcp-*.sql.gz' -type f -mtime "+$BACKUP_KEEP_DAYS" -print -delete | wc -l)
    [ "$removed" -gt 0 ] && log "ลบ backup เก่า $removed ไฟล์ (เกิน $BACKUP_KEEP_DAYS วัน)"
    return 0
}

seconds_until_next_run() {
    local now target
    now=$(date '+%s')
    target=$(date -d "today $BACKUP_AT" '+%s')
    # ถ้าเวลาวันนี้ผ่านไปแล้ว ให้รอรอบพรุ่งนี้
    [ "$target" -le "$now" ] && target=$(date -d "tomorrow $BACKUP_AT" '+%s')
    echo $((target - now))
}

trap 'log "ได้รับสัญญาณหยุด — ออก"; exit 0' TERM INT

log "ตั้งเวลา backup ทุกวัน $BACKUP_AT (TZ=${TZ:-UTC}), เก็บ $BACKUP_KEEP_DAYS วัน"

while true; do
    wait_for=$(seconds_until_next_run)
    log "รอบถัดไปในอีก $((wait_for / 3600)) ชม. $(((wait_for % 3600) / 60)) นาที"
    # background + wait เพื่อให้ trap ทำงานได้ระหว่าง sleep
    sleep "$wait_for" &
    wait $!
    run_backup || log "ข้ามรอบนี้ จะลองใหม่รอบหน้า"
done
