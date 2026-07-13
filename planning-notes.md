# Planning notes

Running notes from planning discussions — forward-looking decisions (some firm, some
tentative) and open questions that are **not yet reflected in code**. For shipped status see
[`ROADMAP.md`](ROADMAP.md); for the API/client contract see [`ios-app-plan.md`](ios-app-plan.md).
Newest first. Legend: ✅ decided · 🟡 leaning · ❓ open.

---

## 2026-06 — Client architecture, hosting & guest accounts

Context: v1.1 is shipped (user-created exercises + guest accounts + RLS lockout; commits
through `464e9a2`). Discussion was about what to build next and how to run it.

### 1. Next focus — build the iOS client, not v1.5 yet ✅ (direction)

Park v1.5 (programs) and build the **native iOS client** next. Rationale: the backend is
already a complete, usable tracker (logging + bidirectional history + custom exercises); a
real client validates the API in ways tests can't (response shapes, the auth/refresh loop,
error handling) — cheaper to find those against 4 slices than after 8. Programs (v1.5) and
coaching (v2) target a **future coach/client web app**, not this iOS app. Full contract for
the client is in [`ios-app-plan.md`](ios-app-plan.md).

### 2. Hosting / deploy 🟡 (leaning always-on; provider open)

Core finding: **free + always-on + JVM is the hard combo** — most *free* PaaS tiers are free
because they scale to zero, which is the cold start we want to avoid. So "no cold start"
effectively means **always-on**.

- **Recommendation: run always-on** so cold start simply never happens.
- Options (verified mid-2026):
  - **Oracle Cloud Always Free (Ampere ARM)** — free, always-on VM; ~2 OCPU / 12 GB after the
    2026-06-15 free-tier reduction (still ample). Trade: you manage the VM; ARM image needed;
    provisioning can be finicky. Best *free* option.
  - **Hetzner** ~€3.50/mo (CX23, ~4 GB) — cheapest solid always-on VPS; you manage it.
  - **Fly.io** ~$2–5/mo (deploys the existing Dockerfile; set `min_machines_running=1`) or
    **Render** $7/mo always-on — lowest-ops, "don't manage a server" choices.
  - Free PaaS tiers (Render free, etc.) sleep after ~15 min → 30–50 s wake — **rejected** for
    the cold-start reason.
- Co-locate the host with the Supabase project region (each request does SQL round-trips).
- Prod config is already env-var driven (`SPRING_DATASOURCE_URL`, `SUPABASE_JWKS_URI`); the
  `local`/`prod` Spring profile split is the remaining "deploy to cloud" groundwork.
- At deploy: V4 RLS lockout applies via Flyway on first boot; also flip Supabase's
  project-level **"Enable RLS on new tables"** toggle.

### 3. Cold start & caching — the reality check 📌 (reference)

- **Measured boot: ~2.0 s** locally (`Started WorkoutApiApplicationKt in 2.035 seconds`);
  Flyway adds only ~0.03 s. On a cheap shared-vCPU box expect ~4–8 s; scale-to-zero adds
  container start → realistically **~5–12 s** first-request latency after idle for a JVM.
  (These are seconds, not the sub-second numbers lightweight Go/Node apps get.)
- **Local caching does NOT fix cold start.** Cold start = JVM boot + Spring init + classload,
  which caching can't touch; and the cache is empty on a fresh process anyway (warming it at
  boot only makes startup slower). Caching helps **steady-state** read latency, not cold start.
- Levers to shrink/eliminate startup, in order: **always-on** (eliminates it) → Spring
  **CDS/AOT** (~20–40 % faster boot, low effort) → **GraalVM native image** (~50–150 ms boot,
  makes scale-to-zero viable — but a real project; native work is parked) → **CRaC** (niche).
- Caching worth doing anyway (for warm perf + lighter Supabase load), when we get to it:
  **global exercise catalog** (`created_by IS NULL`, static) is the high-value, low-risk
  target; JWKS is already cached by the Nimbus decoder; per-user data — skip initially.

### 4. Offline-first client 🟡 (proposed, staged)

Idea: the client uses a **local DB** and **syncs** with the backend, so it works offline (a
gym app benefits — flaky connectivity). Good fit, but sync is genuinely hard.

- Local DB on **native iOS = GRDB.swift (SQLite) or SwiftData** — *not* `sqflite` (that's
  Flutter). (If `sqflite` signals reconsidering Flutter, that's a separate call — see Open.)
- What full sync requires: **client-generated UUIDs** (server upserts by id, vs today's
  DB-assigned id), `updated_at` + soft-delete/**tombstones** for change tracking, a
  **delta sync** endpoint (server still re-validates ownership + measurement combos), and
  **FK-ordered** sync (exercises → workouts → workout_exercises → sets). Multi-device is where
  conflict resolution gets hard; single-device is mostly last-write-wins.
- **Recommended staging:** Phase 1 = local-first + **one-way upload on sign-up** (bulk upsert,
  no multi-device, ~no conflict resolution) → 90 % of the value cheaply. Phase 2 = full
  bidirectional/multi-device sync later, only if needed.
- Decide **before** building much of the client — offline-first vs thin-online are very
  different app architectures.

### 5. Guest accounts — correction + proposal ❓ (open)

- **Correction (fact):** Supabase anonymous sign-ins are **not** a shared user. Each
  `signInAnonymously()` creates a distinct `auth.users` row with its own id; every guest is
  isolated by the same ownership scoping as real users. The current guest implementation
  already gives each person private data.
- Genuine downsides of anon accounts remain: device-bound & unrecoverable (reinstall / switch
  device orphans the data), and they accumulate in `auth.users` (abuse surface).
- **Proposal (ties to #4):** drop Supabase anonymous accounts; **no-account = local-only** (no
  cloud, no BE calls), **sign-in = sync**. This also **simplifies the backend**: remove the
  `is_anonymous` → `ROLE_GUEST/ROLE_MEMBER` split (`SupabaseRoleConverter`), the anon config,
  and the member-gate on `POST /api/exercises` — every API caller becomes a real member.
  Cost: shifts complexity to the client (local DB + sync).

### Open questions

- ❓ **Client stack:** native iOS (current decision) vs Flutter (the `sqflite` mention).
  Decide before starting the client.
- ❓ **Offline-first:** commit to it (and Phase 1 scope) or stay thin-online first?
- ❓ **Hosting provider:** Oracle free vs Hetzner vs Fly/Render — depends on free-vs-low-ops
  preference.
- ❓ **Drop guest accounts** in favor of local-only (only if offline-first is adopted).

### Candidate next actions (none committed)

- Write the `local`/`prod` Spring profile split + document deploy env vars (unblocks any host).
- If offline-first is adopted: an ADR + the BE changes (client UUIDs/upsert, `updated_at` +
  tombstones, sync endpoint) and removal of the guest role/anon config.
- Add global-catalog caching (small, independent of the above) for warm-path perf.
