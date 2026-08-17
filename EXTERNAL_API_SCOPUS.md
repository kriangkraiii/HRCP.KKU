# External API — Scopus Publications (Consumer Guide)

REST API for pulling faculty members' Scopus publications from the Fund Management platform.
This guide is for the **calling system's developers**. Authentication is by **API key**, and
each request returns a flat, paginated list you can store and filter however your system needs.

> You design and create your own storage. This document describes the **API response fields**
> (the contract). We do not expose our internal database schema — build your tables from the
> field reference below. A suggested table is included as a starting point.

## Base URL

```
https://<host>/api/ext/v1
```

All endpoints are versioned; breaking changes ship under a new version (`/api/ext/v2`).

## Authentication

Send your API key as a Bearer token on every request:

```
Authorization: Bearer <your-api-key>
```

- The key looks like `fsk_XXXXXXXXXXXXXXXXXXXXXX`. Treat it as a secret — never embed it in
  front-end code, URLs, or logs.
- Keys can be rotated: you may receive a new key while the old one still works, then the old one
  is revoked. Switch when you receive the new one.
- **HTTPS only** in production.

## Endpoint: List Scopus publications

```
GET /api/ext/v1/scopus/publications
```

Returns a **flat, paginated** list — one row per (faculty member, publication). A publication
co-authored by several faculty members in our system appears **once per member**, so the natural
unique key of a row is `(user_id, eid)`.

### Query parameters

| Param       | Required | Description |
|-------------|----------|-------------|
| `user_ids`  | ✅       | Comma-separated list of faculty `user_id`s (our system's IDs). Duplicates ignored. |
| `year_from` | ✅       | Start year (inclusive), 4 digits. Based on the publication's Scopus cover date. |
| `year_to`   | ❌       | End year (inclusive). Defaults to the current year. |
| `limit`     | ❌       | Page size. Default `50`, max `500`. |
| `offset`    | ❌       | Rows to skip. Default `0`. |

`user_id`s are the platform's stable primary keys; obtain the person → `user_id` mapping from
the platform operator.

### Example request

```bash
curl -H "Authorization: Bearer fsk_XXXXXXXXXXXX" \
  "https://<host>/api/ext/v1/scopus/publications?user_ids=101,102&year_from=2020&year_to=2024&limit=50&offset=0"
```

### Example response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "user_id": 101,
      "user_name": "Somchai Jaidee",
      "user_email": "somchai@kku.ac.th",
      "user_scopus_id": "55680000000",
      "document_id": 2201,
      "title": "Deep Learning for X",
      "publication_name": "IEEE Access",
      "abstract": "We propose a method for ...",
      "aggregation_type": "Journal",
      "subtype_description": "Article",
      "eissn": "21693536",
      "volume": "11",
      "issue": "3",
      "authkeywords": "[\"deep learning\",\"x\"]",
      "openaccess": 1,
      "publication_year": 2023,
      "cover_date": "2023-05-01T00:00:00Z",
      "cited_by": 12,
      "doi": "10.1109/ACCESS.2023.0000000",
      "eid": "2-s2.0-85000000000",
      "scopus_id": "85000000000",
      "scopus_url": "https://www.scopus.com/record/display.uri?eid=2-s2.0-85000000000&origin=inward",
      "author_names": "Somchai Jaidee | Jane Doe",
      "affiliation_name": "Khon Kaen University",
      "cite_score_quartile": "Q1"
    }
  ],
  "paging": { "total": 240, "limit": 50, "offset": 0 },
  "filters": { "user_ids": [101, 102], "year_from": 2020, "year_to": 2024 }
}
```

Nullable fields are **omitted** from the JSON when empty — do not assume every field is present
on every row.

### Response field reference

Each element of `data[]` has the following fields. Use this to design your storage.

**Faculty member (who the publication belongs to)**

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `user_id` | integer | no | Faculty member's ID in our platform. Stable. Part of the row's unique key. |
| `user_name` | string | no | Display name. |
| `user_email` | string | no | Email. |
| `user_scopus_id` | string | yes | The member's Scopus Author ID. |

**Publication (the Scopus document)**

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `document_id` | integer | no | Our internal id for the document. Stable within our system, but prefer `eid` as the cross-system key. |
| `eid` | string | no | Scopus EID — globally unique per document. **Recommended natural key.** |
| `scopus_id` | string | yes | Scopus record id (numeric part of the EID). |
| `scopus_url` | string | yes | Direct link to the Scopus record. |
| `title` | string | no | Publication title. |
| `publication_name` | string | yes | Journal / source name. |
| `publication_year` | integer | yes | Year derived from the cover date. Convenient for filtering. |
| `cover_date` | datetime (ISO 8601) | yes | Scopus cover date. |
| `doi` | string | yes | DOI. |
| `cited_by` | integer | yes | Citation count at time of fetch. |
| `author_names` | string | yes | All authors, `" | "`-separated (in author order). |
| `source_id` | string | yes | Scopus source id. |

**Bibliographic detail**

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `abstract` | string | yes | Abstract text. |
| `aggregation_type` | string | yes | Source type, e.g. `Journal`, `Conference Proceeding`, `Book`. |
| `subtype` | string | yes | Scopus subtype code, e.g. `ar` (article), `cp` (conference paper), `re` (review). |
| `subtype_description` | string | yes | Human-readable subtype, e.g. `Article`. |
| `issn` / `eissn` / `isbn` | string | yes | Serial / book identifiers (digits only, no dash). |
| `volume` / `issue` | string | yes | Journal volume / issue. |
| `page_range` | string | yes | Page range, e.g. `120-135`. |
| `article_number` | string | yes | Article number (used by e-journals instead of page range). |
| `authkeywords` | string (JSON array) | yes | Author keywords as a JSON-array string, e.g. `["term a","term b"]`. |
| `fund_acr` | string | yes | Funding body acronym. |
| `fund_sponsor` | string | yes | Funding sponsor name. |
| `openaccess` / `openaccess_flag` | integer (0/1) | yes | Open-access indicators. |

**Affiliations** (document-level values are aggregated across authors; `user_affiliation_*` is
the querying member's own affiliation on this document)

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `affiliation_afid` / `affiliation_name` / `affiliation_city` / `affiliation_country` / `affiliation_url` | string | yes | Document-level affiliation(s). Multiple values are `" | "`-separated. |
| `affiliations_json` | string (JSON) | yes | Structured list of affiliations, if you want the detail. |
| `user_affiliation_afid` / `user_affiliation_name` / `user_affiliation_city` / `user_affiliation_country` / `user_affiliation_url` | string | yes | The querying faculty member's affiliation on this document. |

**Conference** (present for conference papers)

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `conference_name` / `conference_venue` / `conference_city` / `conference_country` / `conference_location` | string | yes | Conference details. |

**CiteScore metrics** (journal-level, for the relevant year)

| Field | Type | Nullable | Meaning |
|-------|------|----------|---------|
| `cite_score_percentile` | number | yes | CiteScore percentile. |
| `cite_score_quartile` | string | yes | Quartile, e.g. `Q1`. |
| `cite_score_status` | string | yes | `Complete` / `In-Progress`. |
| `cite_score_rank` | integer | yes | Rank within category. |

### Suggested storage on your side (optional — adapt freely)

You own your schema; this is just a convenient starting point. One row per (member, publication),
keyed on `(user_id, eid)`:

```sql
CREATE TABLE scopus_publications (
  user_id           INT          NOT NULL,
  eid               VARCHAR(64)  NOT NULL,   -- Scopus global id (natural key)
  scopus_id         VARCHAR(64),
  scopus_url        VARCHAR(512),
  title             VARCHAR(1000) NOT NULL,
  publication_name  VARCHAR(512),
  publication_year  SMALLINT,
  cover_date        DATE,
  doi               VARCHAR(255),
  cited_by          INT,
  author_names      TEXT,
  cite_score_quartile VARCHAR(8),
  raw_json          JSON,                    -- optional: keep the whole row for fields you skip
  fetched_at        DATETIME     NOT NULL,
  PRIMARY KEY (user_id, eid)
);
```

Keep only the columns you need; stash the rest in a `raw_json` column if you want to avoid schema
churn when we add fields. On each sync, upsert on `(user_id, eid)`.

### Error format

All errors use a consistent envelope:

```json
{ "success": false, "error": "<message>", "code": "<CODE>" }
```

| HTTP | `code`                 | Meaning |
|------|------------------------|---------|
| 401  | `MISSING_API_KEY`      | No `Authorization: Bearer` header. |
| 401  | `INVALID_API_KEY`      | Key unknown, revoked, or expired. |
| 403  | `INSUFFICIENT_SCOPE`   | Key is valid but lacks the required scope. |
| 403  | `HTTPS_REQUIRED`       | Request was not over HTTPS (when enforced). |
| 422  | `INVALID_PARAMETER`    | Missing/invalid `user_ids`, `year_from`, `year_to`, etc. |
| 429  | `RATE_LIMIT_EXCEEDED`  | Too many requests. Respect the `Retry-After` header (seconds). |
| 500  | —                      | Internal error. |

### Pagination

Use `paging.total` to iterate: increase `offset` by `limit` until `offset >= total`.

### Rate limiting

Each client has a fixed request budget per minute (default **100**). Exceeding it returns `429`
with a `Retry-After` header (seconds). Page steadily rather than in bursts.
