# External API — Faculty Directory (Consumer Guide)

REST API for pulling the faculty (users) directory from the Fund Management platform so an
external system can keep a synced copy. This guide is for the **calling system's developers**.

> Intended use: **periodic master-data sync**, not per-request lookups. Pull on a schedule
> (e.g. a nightly/hourly cron), store the result in your own database, and have your app read
> from that local copy. Do **not** call this API on every page load.

## Base URL

```
https://<host>/api/ext/v1
```

Versioned; breaking changes ship under a new version (`/api/ext/v2`).

## Authentication

Send your API key as a Bearer token on every request:

```
Authorization: Bearer <your-api-key>
```

- The key looks like `fsk_XXXXXXXXXXXXXXXXXXXXXX`. Keep it secret (backend only — never in
  front-end code, URLs, or logs).
- Call server-to-server from your backend; your own site does **not** need a registered domain
  or HTTPS to consume this API. (Our endpoint should be HTTPS in production so the key is not
  exposed in transit.)

## Endpoint: List faculty

```
GET /api/ext/v1/users
```

Returns a **flat, paginated** list of faculty. Soft-deleted users and internal test/system
accounts are excluded automatically. Each record's stable key is `user_id`.

### Query parameters

| Param           | Required | Description |
|-----------------|----------|-------------|
| `updated_since` | [NO]       | RFC 3339 datetime. Returns only users modified at/after this time (incremental sync). Omit for a full pull. |
| `limit`         | [NO]       | Page size. Default `100`, max `500`. |
| `offset`        | [NO]       | Rows to skip. Default `0`. |

### Sync strategy (recommended)

1. **First run:** full pull — call without `updated_since`, paging with `limit`/`offset` until
   you have everyone.
2. **Subsequent runs:** incremental — pass `updated_since` = the max `updated_at` you have
   stored, to fetch only what changed.
3. **Periodic reconcile:** incremental sync cannot detect deletions. Do a **full pull on a
   slower cadence** (e.g. weekly) and remove/deactivate on your side any `user_id` not present.
4. Use `is_active` to decide who is currently active — we return all non-deleted users and let
   you filter.

### Example request

```bash
curl -H "Authorization: Bearer fsk_XXXXXXXXXXXX" \
  "https://<host>/api/ext/v1/users?updated_since=2026-01-01T00:00:00Z&limit=100&offset=0"
```

### Example response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "user_id": 1001,
      "prefix": "ผศ.ดร.",
      "user_fname": "งามนิจ",
      "user_lname": "อาจอินทร์",
      "gender": "female",
      "email": "ngamnij@kku.ac.th",
      "tel": "043000000",
      "tel_format": "0-4300-0000",
      "position_title": "ผู้ช่วยศาสตราจารย์",
      "position_en": "Assistant Professor",
      "prefix_position_en": "Asst. Prof.",
      "manage_position": "รองหัวหน้าภาควิชา",
      "name_en": "Ngamnij Arch-int",
      "suffix_en": "Ph.D.",
      "scopus_id": "6506313280",
      "scholar_author_id": "xxxxxxx",
      "lab_name": "Data Engineering Lab",
      "room": "IT-401",
      "cp_web_id": "12345",
      "role_id": 1,
      "role_name": "teacher",
      "is_active": "1",
      "updated_at": "2026-07-01T09:30:00+07:00"
    }
  ],
  "paging": { "total": 320, "limit": 100, "offset": 0 },
  "filters": { "updated_since": "2026-01-01T00:00:00Z" }
}
```

Nullable fields are **omitted** when empty — don't assume every field is present.

### Response field reference

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `user_id` | integer | no | Stable primary key. Use as your sync key. Same id used by the Scopus endpoint. |
| `prefix` | string | yes | Thai name prefix (คำนำหน้า). |
| `user_fname` / `user_lname` | string | no | First / last name (Thai). |
| `gender` | string | yes | Gender. |
| `email` | string | yes | Email. **Personal data** — handle per your agreement. |
| `tel` | string | yes | Phone number. **Personal data.** |
| `tel_format` | string | yes | Formatted phone number. |
| `position_title` | string | yes | Academic position (Thai), e.g. `ผู้ช่วยศาสตราจารย์`. |
| `position_en` | string | yes | Academic position (English). |
| `prefix_position_en` | string | yes | Position prefix (English), e.g. `Asst. Prof.`. |
| `manage_position` | string | yes | Management/administrative position (Thai), if any. |
| `name_en` | string | yes | Full name (English). |
| `suffix_en` | string | yes | Name suffix (English), e.g. `Ph.D.`. |
| `scopus_id` | string | yes | Scopus Author ID (use with the Scopus publications endpoint). |
| `scholar_author_id` | string | yes | Google Scholar author id. |
| `lab_name` | string | yes | Lab name. |
| `room` | string | yes | Office/room. |
| `cp_web_id` | string | yes | Internal CP web id. |
| `role_id` | integer | no | Role id in our system. |
| `role_name` | string | yes | Role name, e.g. `teacher`, `staff`, `admin`, `dept_head`, `executive`. |
| `is_active` | string | yes | Account active flag (`"1"`/`"0"`). Filter active users on your side. |
| `updated_at` | datetime (RFC 3339) | yes | Last modification time. Store the max value to drive `updated_since` next run. |

### Suggested storage on your side (optional — adapt freely)

One row per faculty, keyed on `user_id`, upserted on each sync:

```sql
CREATE TABLE faculty (
  user_id           INT           NOT NULL,
  prefix            VARCHAR(50),
  user_fname        VARCHAR(255)  NOT NULL,
  user_lname        VARCHAR(255)  NOT NULL,
  email             VARCHAR(255),
  position_title    VARCHAR(255),
  position_en       VARCHAR(255),
  name_en           VARCHAR(255),
  scopus_id         VARCHAR(64),
  role_id           INT,
  role_name         VARCHAR(64),
  is_active         TINYINT(1),
  source_updated_at DATETIME,          -- = updated_at from the API
  synced_at         DATETIME NOT NULL, -- when you fetched it
  raw_json          JSON,              -- optional: keep the whole record
  PRIMARY KEY (user_id)
);
```

Keep only the columns you need; stash the rest in `raw_json`. On each sync, `UPSERT` on
`user_id` and track `MAX(source_updated_at)` for the next `updated_since`.

### Error format

```json
{ "success": false, "error": "<message>", "code": "<CODE>" }
```

| HTTP | `code`                 | Meaning |
|------|------------------------|---------|
| 401  | `MISSING_API_KEY`      | No `Authorization: Bearer` header. |
| 401  | `INVALID_API_KEY`      | Key unknown, revoked, or expired. |
| 403  | `INSUFFICIENT_SCOPE`   | Key lacks the `users.read` scope. |
| 422  | `INVALID_PARAMETER`    | Invalid `updated_since` (must be RFC 3339). |
| 429  | `RATE_LIMIT_EXCEEDED`  | Too many requests. Respect the `Retry-After` header (seconds). |
| 500  | —                      | Internal error. |

### Pagination

Use `paging.total` to iterate: increase `offset` by `limit` until `offset >= total`.

### Rate limiting

Each client has a fixed request budget per minute (default **100**). A scheduled batch sync
should stay well under this — page steadily, don't burst.
