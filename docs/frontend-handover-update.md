# Frontend Handover Update — Worker / KYC / Face Enrollment + API Doc Refresh

This is a short, plain-language summary of everything that changed in the backend's API documentation
this round — it is **not** a full API reference. For the full details of every request/field/error
code, use:

- **Postman** (`postman/` folder in the backend repo) — import `ATW-Local.postman_environment.json`
  plus all the `.postman_collection.json` files. Every request has a description, example response,
  and a note on what permission it needs.
- **Swagger UI**, live at `http://localhost:8080/swagger-ui.html` (or the deployed base URL) — the same
  information, browsable without opening Postman, with a "Try it out" button.

This doc only tells you **what to read where**, and **what's different** from what you may have
already built against.

---

## 1. Things you've already built against — what changed

### Masterdata (departments, industries, designations, skills, skill categories, minimum wage rates)

You already have this working. Nothing about how you call these endpoints needs to break — but there's
more available now than before:

- **Filters can now take multiple values.** Where you used to pass one `industryId`, you can now pass
  `industryIds` (plural) with several ids, same idea for designation/status filters — the UI can offer
  "any of these" instead of one at a time, without extra API calls.
- **Free-text search** (`?search=...`) and **sorting** (`?sort=name,desc` style — see Postman for which
  field names are allowed per screen) are now available on every list.
- **Real delete, not just deactivate.** Every masterdata screen now has an actual Delete button option
  (single and multi-select) — it's blocked with a friendly error message if something else still
  references the row (e.g. "can't delete this department, 3 designations still use it").
- **Worth double-checking on your side:** the Postman docs for the industry/designation filters briefly
  documented the *old* singular param names (`industryId`/`designationId`) even after the API itself had
  already switched to the plural, multiselect versions. If your filter UI was built by copying those
  param names from Postman during that window, it may currently be sending a param name the API quietly
  ignores — meaning the filter dropdown appears to do nothing. Worth a quick manual test against a real
  backend. (Separately, two list endpoints were also documented as returning a plain array when they
  actually return the same paginated shape as everything else — a pure doc fix, not a behavior change.)

**Where to look:** `ATW-Masterdata.postman_collection.json` (updated).

### Staff / Roles

- **Deleting a staff member now "soft deletes" instead of permanently erasing them.** Same `DELETE`
  endpoint, same response — nothing to change in your code. The one thing worth knowing: a deleted
  staff member's email and username become available again for a brand-new account (they weren't
  reliably before).
- A docs correction only (behavior was already like this, the old doc was just wrong): there is **no**
  "account locks after 5 failed password attempts" rule. Login is protected by rate-limiting instead.
  If you built any UI copy assuming a lockout message, it won't ever actually appear.
- **As of 2026-11-17, Staff listing finally has real filters**, not just pagination: `status`
  (multiselect), `roleId` (multiselect — a staff member can hold several roles, matches any of them),
  `superAdmin` (true/false), and free-text `search` (name/email/username/phone). Same request/response
  shape otherwise.
- **As of 2026-11-18, two more Staff list filters: `stateId` / `cityId`** (multiselect, same pattern) —
  matches a staff member whose address (set via `GET/PUT /admin/staff/{id}/address`) is in one of the
  given states/cities. A staff member with no address saved yet never matches either filter.
- **As of 2026-11-18, the Staff list (and Get Staff) now also *display* `city`/`state`** — resolved
  display names from the saved address, the same display-only pattern Worker already has. Both are
  `null` until an address has been saved. The full address (line1/line2/pincode) is still only on
  `GET .../address`, same as before.
- **As of 2026-11-17, Roles listing changed shape — this one needs a code change if you've already
  built it.** `GET /admin/roles` used to return a flat array (`data: [...]`). It now returns the same
  paginated shape every other list uses (`data: { content: [...], page, size, totalElements, ... }`) —
  plus it finally supports `search`, a `systemRole` filter, and sorting. Read `data.content` instead of
  `data` directly, or the roles table will break against the new response.

**Where to look:** `ATW-RBAC-Staff.postman_collection.json` (updated).

### Worker Bank Detail Verification

Confirms a worker's declared bank account against the bank itself (account exists, and the name on the
account matches what was declared) before marking it trusted. This endpoint already existed before this
handover doc started tracking it — documented here for the first time.

- `POST /workers/{id}/bank-detail/verify` — the authoritative check against a real, already-created
  worker's saved bank details. Only this call ever sets `verified`/`verifiedAt`.
- **New as of 2026-11-17:** `POST /workers/bank-detail/check` — a standalone version that takes the
  account number/IFSC/name directly in the request body, with **no worker involved at all** and nothing
  saved. Use this for a quick sanity check before a worker record even exists (e.g. during intake) — it's
  advisory only and never marks anything verified.

**Where to look:** `ATW-Worker.postman_collection.json` — new "Check Bank Account" request added to the
existing Worker Bank Detail folder.

### Login / Account (Auth)

No behavior change at all here — just the same lockout-wording correction as above, carried into this
collection's docs too.

**Where to look:** `ATW-Auth-Account.postman_collection.json` (updated).

---

## 2. Brand new — nothing built against this yet

These three are entirely new screens/features. Each has its own Postman collection with full examples.

### Worker (the core worker/gig-staff record)

Full CRUD for worker profiles: create, update, view, list (with the same filter/search/sort/multiselect
pattern as masterdata above), status changes (with required reasons for suspend/blacklist/deactivate),
designation + skill assignment (up to 3 designations per worker), profile photo, delete (soft, same
reuse-the-mobile-number behavior as staff above), bulk status/delete, and CSV export.

The worker list and detail view also show **KYC status** and **face-enrollment status** (see below) as
read-only fields — you don't call those other modules separately just to show a badge on the worker
list; it's already included in the worker response.

**As of 2026-11-17:**
- The worker **list** (and CSV export) now show **current/permanent city + state** as plain display
  fields (`currentCity`, `currentState`, `permanentCity`, `permanentState`) — city/state only, to keep
  the list light. The worker **detail** view (`GET /workers/{id}`) shows the **full** address for both
  types instead (line1/line2/city/state/pincode) — use the detail call when you need more than city/state.
- Two new list filters: `stateId` / `cityId` (multiselect, same pattern as everything else) — matches a
  worker whose *either* address (current or permanent) is in one of the given states/cities.
- **Worker notes can now be deleted**, not just listed/added: `DELETE /workers/{id}/notes/{noteId}`. This
  is a genuine hard delete (the note row is actually gone, unlike a worker record's own soft delete) and
  needs its own new permission — see §3 below, it is **not granted to any role by default**, so this
  button will 403 for everyone until an admin assigns it on the Roles screen.

**Where to look:** `ATW-Worker.postman_collection.json` (new/updated).

### Worker KYC (identity verification)

Two things live here:
1. **Live Aadhaar e-KYC**: send an OTP to the worker's Aadhaar-linked mobile, verify it, and the
   government-backed response auto-fills name/DOB/gender/address/photo straight into the "create
   worker" form. This is the recommended way to onboard a worker.
2. **Manual fallback**: if live verification isn't available/used, an admin can type in a worker's
   Aadhaar or PAN number directly — it's saved as declared, unverified ("pending"), until an admin or a
   later verification confirms it. PAN numbers are now format-checked (must look like `ABCPE1234F`) and
   checked for duplicates across workers; Aadhaar numbers are duplicate-checked but not format-checked.

**Where to look:** `ATW-Worker-KYC.postman_collection.json` (new).

### Worker Face Enrollment

Upload a photo of a worker's face (with their explicit consent) to enroll a biometric template, used
later for attendance face-matching. Three endpoints: check status, enroll/re-enroll (re-enrolling fully
replaces the old one), and remove. A bad-quality or clearly-not-a-live-photo image is rejected outright
with a clear error, nothing gets saved. **As of 2026-11-17**, enroll/re-enroll can also fail with a 409
`CONFLICT` if the photo matches a face already enrolled to a *different* worker (worth a distinct error
message in the UI, not lumped in with the 400/422 cases) — nothing gets saved in that case either.

A fourth endpoint, **Identify Face** (`POST /admin/face-identify`, its own `faceauth.identify`
permission), added 2026-11-16: upload any photo and get back which enrolled worker it best matches, if
any — "whose face is this" rather than "does this one worker have a template." No `workerId` in the
request; it searches every enrolled worker platform-wide. `matched: false` is a normal response, not an
error.

**Where to look:** `ATW-Worker-FaceAuth.postman_collection.json` (updated).

---

## 3. If you manage the Roles/Permissions screen

Three new permission "groups" now exist in the permission catalog, for assigning to roles:

- **`workers`** — view, create, edit, toggle_status, delete, export, view_comments, delete_comments (the
  last one gates deleting a worker note, see above — also not granted to any role by default)
- **`kyc`** — view, submit
- **`faceauth`** — view, enroll, identify (the last one gates the Identify Face endpoint above)

If your Roles screen renders whatever the permission-catalog endpoint returns, this should show up
automatically with no code change. If it has any hardcoded list of modules, these three need adding.

---

## 4. Quick checklist for picking this up

1. Pull the latest Postman collections + environment file from `postman/`.
2. Re-import into Postman (or just re-open if already imported — the files are the same ones you had,
   just updated/added to).
3. Log in via **Auth & Account**'s Login + Verify OTP requests first (every other request needs the
   token that saves).
4. New work: build against **Worker**, **Worker KYC**, and **Worker Face Enrollment**.
5. Existing screens: no required changes, but consider using the new multiselect filters/search/sort/
   bulk actions where it'd improve the UI, and double-check nothing in your code assumed the old
   singular `industryId`/`designationId` filter names.
6. **One exception to "no required changes":** if you've already built the Roles screen, update it now
   for the new paginated response shape (see Staff / Roles above) — read `data.content`, not `data`.
