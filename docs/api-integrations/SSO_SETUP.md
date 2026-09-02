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

ขอจากสำนักเทคโนโลยีดิจิทัล มข. (ติดต่อ: teerpo@kku.ac.th)

ระบบ deploy เป็น **Windows Service `MyWebApp`** รัน `C:\apps\myspringboot\app.jar`
(`.github/workflows/deploy.yml` build แล้วทับไฟล์ jar เมื่อ push เข้า branch `deploy`)
ทุกค่าใน `application.properties` เป็นรูป `${ENV_VAR:default}` จึงตั้งทับได้ 2 ทาง

### วิธีที่ 1 — ไฟล์ properties ข้างนอก jar (แนะนำ)

ค่าไม่ปนอยู่ใน jar ที่ถูกทับทุกครั้งที่ deploy และตั้ง permission เฉพาะไฟล์นี้ได้

สร้าง `C:\apps\myspringboot\config\application.properties` (ตั้งค่า **property** ตรง ๆ ไม่ใช่ชื่อ env)

```properties
app.auth.mode=production
app.auth.sso.allowed-emails=admin@kku.ac.th,support@kku.ac.th
kku.sso.stage=uat
kku.sso.app-id=...
kku.sso.client-id=...
kku.sso.client-secret=...
kku.sso.redirect-login-url=https://hrd.computing.kku.ac.th/auth/callback/login
kku.sso.redirect-logout-url=https://hrd.computing.kku.ac.th/auth/callback/logout
```

Spring Boot อ่านโฟลเดอร์ `config\` **เทียบจาก working directory ของโปรเซส** ซึ่ง Windows Service
มักตั้งเป็น `C:\Windows\System32` ถ้าไม่ได้กำหนด — ถ้าไม่แน่ใจให้ระบุตรง ๆ ไปเลยที่ argument ของ service

```
-Dspring.config.additional-location=file:///C:/apps/myspringboot/config/
```

จำกัดสิทธิ์ไฟล์ (มี client secret อยู่ข้างใน):

```powershell
icacls C:\apps\myspringboot\config\application.properties /inheritance:r
icacls C:\apps\myspringboot\config\application.properties /grant "SYSTEM:(R)" "Administrators:(F)"
```

### วิธีที่ 2 — environment variable ระดับเครื่อง

ใช้ได้ไม่ว่า service จะถูกสร้างด้วยอะไร เพราะ service รับ environment ของเครื่องตอน start

```powershell
[Environment]::SetEnvironmentVariable('APP_AUTH_MODE','production','Machine')
[Environment]::SetEnvironmentVariable('SSO_ALLOWED_EMAILS','admin@kku.ac.th,support@kku.ac.th','Machine')
[Environment]::SetEnvironmentVariable('KKU_SSO_STAGE','uat','Machine')
[Environment]::SetEnvironmentVariable('KKU_SSO_APP_ID','...','Machine')
[Environment]::SetEnvironmentVariable('KKU_SSO_CLIENT_ID','...','Machine')
[Environment]::SetEnvironmentVariable('KKU_SSO_CLIENT_SECRET','...','Machine')
Restart-Service MyWebApp
```

`Machine` เท่านั้น — ตั้งแบบ `User` service มองไม่เห็น เพราะรันคนละ account

**ทั้งสองวิธีต้อง `Restart-Service MyWebApp`** ค่าถูกอ่านตอน start ครั้งเดียว
เช็คว่าเข้าจริงไหม: ดู log ตอน start ถ้า SSO ตั้งไม่ครบ `SsoAuthController` จะ log
`SSO login attempted but configuration is incomplete: <ชื่อค่าที่ขาด>` ตอนมีคนกดปุ่ม

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
# คั่นด้วย , เว้นวรรคได้ (ระบบ trim ให้) และไม่สนตัวพิมพ์เล็กใหญ่
app.auth.sso.allowed-emails=admin@kku.ac.th,support@kku.ac.th
```

ถ้าไม่เข้าเงื่อนไข → ปฏิเสธ ไม่สร้างบัญชีให้
บัญชีที่ถูก**ปิดใช้งาน** (`isEnable=false`) ก็เข้าไม่ได้ แม้จะยังอยู่ในรายชื่ออาจารย์หรือใน allowlist

### จุดที่พลาดกันบ่อย: อยู่ใน allowlist ไม่ได้แปลว่าเป็นแอดมิน

บัญชีใหม่ที่ระบบสร้างให้อัตโนมัติได้ role `ROLE_USER` **เสมอ** — `SSO_ALLOWED_EMAILS` แค่บอกว่า
"เข้าได้" ไม่ได้บอกว่า "เป็นแอดมิน" ถ้าแอดมินล็อกอิน SSO ครั้งแรกโดยที่ยังไม่มีบัญชีในระบบ
จะได้หน้าผู้ใช้ธรรมดา และไม่มีใครกดเลื่อนสิทธิ์ให้ได้เลย (ไก่กับไข่)

ทำอย่างใดอย่างหนึ่ง **ก่อน** เปิดโหมด production:

1. ตั้ง `app.admin.email` (env `ADMIN_EMAIL`) เป็นอีเมล @kku.ac.th ที่จะใช้ล็อกอิน SSO จริง
   → ตอน start `AdminInitializer` สร้างบัญชี `ROLE_ADMIN` ให้ แล้วล็อกอิน SSO ทับบัญชีเดิมนั้น
   (การล็อกอิน SSO ไม่เปลี่ยน role ของบัญชีที่มีอยู่แล้ว — role เป็นการตัดสินใจฝั่งเรา)
2. หรือเลื่อนสิทธิ์บัญชีนั้นเป็นแอดมินจากหน้าจัดการผู้ใช้ ตอนที่ยังล็อกอินด้วยรหัสผ่านได้อยู่

แล้วค่อยใส่อีเมลเดียวกันนั้นใน allowlist (ถ้าไม่ใช่อาจารย์ที่มีใน `fs_faculty`)

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

**ถ้าผู้ใช้เข้าอีเมลรับ OTP ไม่ได้** (เมลเด้ง / เปลี่ยนอีเมล / ลาออก) เขาจะเข้าระบบไม่ได้เลย
และไปปิดสวิตช์เองก็ไม่ได้เพราะต้องล็อกอินก่อน — แอดมินกดปิดให้ได้ที่หน้า `/admin/users`
ปุ่ม **"รีเซ็ต 2FA"** (ขึ้นเฉพาะบัญชีที่เปิด 2FA ไว้) ระบบล้างรหัส OTP ที่ค้างอยู่ให้ด้วย
และเขียน `RESET_2FA` ลง activity log ว่าแอดมินคนไหนกดให้ใคร

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
- [ ] ตั้ง allowlist สำหรับแอดมิน **และ** บัญชีนั้นเป็น `ROLE_ADMIN` แล้วจริง ๆ (ดูหัวข้อ 3)
- [ ] `Restart-Service MyWebApp` หลังแก้ค่า แล้วลองกดปุ่ม SSO ดูว่าไม่ขึ้น "ยังไม่ได้ตั้งค่าให้สมบูรณ์"
- [ ] SMTP ใช้งานได้จริง (ถ้ามีคนเปิด 2FA ไว้ OTP ส่งทางอีเมลทางเดียว ส่งไม่ออก = เข้าระบบไม่ได้)
- [ ] รันบน HTTPS จริง (cookie ตั้ง `Secure` ไว้ ถ้าเป็น HTTP เบราว์เซอร์จะไม่ส่ง cookie กลับมา)
- [ ] เปลี่ยนรหัส keystore จากค่า default
