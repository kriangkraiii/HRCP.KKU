# การตั้งค่า KKU SSO และการขึ้น Production

## 1. โหมดการล็อกอิน

ระบบมี 2 โหมด สลับด้วย `app.auth.mode` ใน `application.properties`

| โหมด | ค่า | พฤติกรรม |
|---|---|---|
| พัฒนา | `dev` (ค่าเริ่มต้น) | ล็อกอินด้วย email/password แบบเดิม มีหน้าลืมรหัสผ่าน / เข้าครั้งแรก ครบ |
| ใช้งานจริง | `production` | เหลือปุ่ม **"เข้าสู่ระบบด้วย KKU SSO"** ปุ่มเดียว ปิดการล็อกอินด้วยรหัสผ่านทั้งหมด |

```properties
app.auth.mode=${APP_AUTH_MODE:dev}
```

โหมด `production` ไม่ได้แค่ซ่อนฟอร์ม แต่**ปิด endpoint จริง**:
`POST /login` ตอบ 403, และ `/forgot-password`, `/first-login`, `/reset-password` เข้าไม่ได้
(ถ้าซ่อนแต่ปล่อย endpoint ไว้ การเดารหัสผ่านก็ยังทำได้อยู่)

> `/2fa/**` **ไม่ถูกปิด** เพราะ OTP เป็นปัจจัยที่สองของการล็อกอินทั้งสองแบบ ไม่ใช่ของรหัสผ่านอย่างเดียว
> ดูหัวข้อ 5

## 2. ค่าที่ต้องกรอก

ขอจากสำนักเทคโนโลยีดิจิทัล มข. (ติดต่อ: teerpo@kku.ac.th) แล้วใส่เป็น environment variable

```bash
export APP_AUTH_MODE=production
export KKU_SSO_STAGE=uat            # uat ตอนทดสอบ, prod ตอนใช้จริง
export KKU_SSO_APP_ID=...
export KKU_SSO_CLIENT_ID=...
export KKU_SSO_CLIENT_SECRET=...
export KKU_SSO_REDIRECT_LOGIN_URL=https://hrd.computing.kku.ac.th/auth/callback/login
export KKU_SSO_REDIRECT_LOGOUT_URL=https://hrd.computing.kku.ac.th/auth/callback/logout
```

> `KKU_SSO_REDIRECT_LOGIN_URL` **ต้องตรงกับที่กรอกในแบบฟอร์มขอใช้บริการ** เพราะระบบส่งค่านี้ไปให้ SSO ตรวจซ้ำตอนแลก token

**URL ที่ต้องลงทะเบียนกับ มข.**
- Redirect URL: `https://<โดเมนของเรา>/auth/callback/login`
- Redirect Logout URL: `https://<โดเมนของเรา>/auth/callback/logout`

## 3. ใครเข้าระบบได้บ้าง

KKU SSO ยืนยันตัวตนคนทั้งมหาวิทยาลัย รวมนักศึกษา — **การล็อกอินผ่านไม่ได้แปลว่ามีสิทธิ์เข้าระบบนี้**
ระบบตรวจซ้ำอีกชั้นแบบ deny-by-default:

1. อีเมลต้องอยู่ในตาราง `fs_faculty` (รายชื่ออาจารย์ที่ sync มาจาก Fund Management) **และ** ยังไม่ถูกระงับ
2. หรืออยู่ใน allowlist ที่ตั้งเพิ่ม สำหรับแอดมินที่ไม่ใช่อาจารย์

```properties
app.auth.sso.allowed-emails=${SSO_ALLOWED_EMAILS:}   # คั่นด้วย , เช่น admin@kku.ac.th,support@kku.ac.th
```

ถ้าไม่เข้าเงื่อนไข → ปฏิเสธ ไม่สร้างบัญชีให้
บัญชีใหม่ที่ผ่านเงื่อนไขจะถูกสร้างอัตโนมัติ (role `ROLE_USER`) โดยดึงชื่อ/ตำแหน่งจาก `fs_faculty`

**ก่อนเปิดใช้จริงต้องรัน sync อาจารย์ก่อน** ไม่งั้น `fs_faculty` ว่าง แล้วจะไม่มีใครเข้าได้เลย
(ดูที่หน้า `/admin/external-sync`)

## 4. Flow ที่ระบบทำ

ตามคู่มือ SSONext — ไม่ใช่ OAuth2 มาตรฐาน

```
ผู้ใช้กดปุ่ม
  → GET /auth/sso/login
  → redirect ไป https://sso-uat-web.kku.ac.th/login?app=<AppID>
  → ผู้ใช้ล็อกอินที่ มข.
  → redirect กลับ /auth/callback/login?code=<uuid>
  → POST /auth.token  {code, redirectUrl, clientId, clientSecret}   (ฝั่งเซิร์ฟเวอร์)
  → ตรวจ allowlist ด้วยอีเมลที่ได้
  → สร้าง/อัปเดตบัญชีในระบบ
  → ถ้าบัญชีเปิด 2FA → พักไว้ที่ /2fa/verify (ยังไม่มี session)
  → สร้าง session + เขียน log LOGIN_SUCCESS
```

ออกจากระบบ: ปุ่ม "ออกจากระบบ" (`POST /logout`) ในโหมด SSO จะ redirect ไป `/logout?app=<AppID>` ของ มข. ให้เอง
(ถ้าล้างแค่ฝั่งเรา session ที่ มข. ยังอยู่ กดเข้าใหม่จะเข้าได้เลยโดยไม่ถามรหัส)
`GET /auth/sso/logout` ยังใช้ได้และไปที่เดียวกัน

## 5. 2FA ใช้ร่วมกับ SSO

การล็อกอินที่ มข. คือ**ปัจจัยแรก** เท่านั้น ถ้าผู้ใช้เปิด 2FA ไว้ในหน้าตั้งค่า ระบบยังถาม OTP ต่อ
ไม่ว่าจะเข้ามาทางรหัสผ่านหรือ SSO — สวิตช์เดียวกัน หน้าจอเดียวกัน (`/2fa/verify`)

**2FA เป็น opt-in — ผู้ใช้เปิดเอง ระบบไม่เปิดให้**
บัญชีใหม่ (รวมบัญชีที่สร้างอัตโนมัติจาก SSO) ค่าเริ่มต้นคือปิด
ที่เดียวที่เปลี่ยนค่านี้ได้คือสวิตช์ในหน้าตั้งค่าของเจ้าตัว (`POST /{user|admin}/academic/settings/toggle-2fa`)
และการล็อกอิน SSO ครั้งถัดไปจะไม่ทับค่าที่ผู้ใช้ตั้งไว้ (`SsoUserProvisioner` รีเฟรชแค่ชื่อ)
ถ้าอยากบังคับทั้งระบบต้องเพิ่มโค้ดใหม่ — ตอนนี้ไม่มีทางที่ทำให้เปิดโดยที่ผู้ใช้ไม่ได้กด

```
SSO callback → ผ่าน allowlist → บัญชีเปิด 2FA?
                                  ├─ ไม่ → สร้าง session ทันที
                                  └─ ใช่ → ส่ง OTP ทางอีเมล, ยังไม่สร้าง session
                                           → กรอกถูก → สร้าง session (พร้อม access token ของ SSO)
                                           → ผิดครบ 5 ครั้ง → กลับหน้า /signin
```

จุดที่ต้องระวังเวลาแก้โค้ดต่อ:
- **`/2fa/**` ต้องเปิดไว้ทั้งสองโหมด** ถ้าปิดในโหมด SSO ผู้ใช้ที่เปิด 2FA จะเข้าระบบไม่ได้เลย
- access token ของ SSO ถูกพักไว้ในเซสชันชั่วคราวระหว่างรอ OTP แล้วจึงย้ายเข้าเซสชันจริง
  (ออก token มาก่อนที่ปัจจัยที่สองจะผ่าน จึงต้องไม่ให้หลุดหายและไม่ให้ถือว่าเป็น session)
- โค้ดส่วนที่ใช้ร่วมกันทั้งสองทางอยู่ที่ `SignInService` — ตอนที่ตรรกะ 2FA อยู่ใน
  success handler ของรหัสผ่านอย่างเดียว SSO เดินข้ามไปเฉย ๆ

**บัญชีที่ถูกปิดใช้งาน (`isEnable=false`) เข้าทาง SSO ไม่ได้** แม้จะยังอยู่ในรายชื่ออาจารย์
(ทาง SSO สร้าง `Authentication` เอง ไม่ผ่าน `DaoAuthenticationProvider` จึงต้องเช็คเองที่ `SsoAccessPolicy`)
ส่วนการ **ล็อกชั่วคราวจากการเดารหัสผ่าน ไม่กั้นทาง SSO** เพราะ SSO ไม่ได้ใช้รหัสผ่านในระบบนี้

## 6. ข้อควรรู้เรื่องความปลอดภัยของตัว SSO

**ตัว SSO ไม่มีพารามิเตอร์ `state`** — login endpoint รับแค่ `app=<AppID>` จึงผูก callback กับเบราว์เซอร์ที่เริ่ม flow ไม่ได้ ทำให้ป้องกัน login-CSRF ในระดับโปรโตคอลไม่ได้เต็มที่

สิ่งที่ทำได้และทำแล้ว:
- แลก `code` ฝั่งเซิร์ฟเวอร์ด้วย client secret (code เปิดเผยอย่างเดียวใช้ไม่ได้)
- เปลี่ยน session id ตอนล็อกอินสำเร็จ กัน session fixation
- `code` จะกลายเป็น session ได้เฉพาะเมื่ออีเมลอยู่ใน allowlist

ถ้าต้องการปิดช่องนี้ให้สนิท ต้องขอให้ มข. เพิ่ม `state` ในระบบ SSO

**การตอบกลับของ SSO**: ตอนล้มเหลวมันตอบ **HTTP 200 พร้อม `ok:false`** ไม่ใช่ 4xx
โค้ดจึงเช็คที่ฟิลด์ `ok` เสมอ — ถ้าเช็คแค่ status code จะนับว่าล็อกอินสำเร็จทันที

## 7. เช็กลิสต์ก่อนขึ้น production

- [ ] `APP_AUTH_MODE=production`
- [ ] กรอก `KKU_SSO_APP_ID` / `CLIENT_ID` / `CLIENT_SECRET` ครบ
- [ ] `KKU_SSO_STAGE=prod` (ตอนเลิกทดสอบ UAT แล้ว)
- [ ] redirect URL ตรงกับที่ลงทะเบียนไว้
- [ ] รัน sync อาจารย์แล้ว (`fs_faculty` ต้องมีข้อมูล)
- [ ] ตั้ง `SSO_ALLOWED_EMAILS` สำหรับแอดมิน
- [ ] รันบน HTTPS จริง (cookie ตั้ง `Secure` ไว้ ถ้าเป็น HTTP เบราว์เซอร์จะไม่ส่ง cookie กลับมา)
- [ ] เปลี่ยนรหัส keystore จากค่า default
