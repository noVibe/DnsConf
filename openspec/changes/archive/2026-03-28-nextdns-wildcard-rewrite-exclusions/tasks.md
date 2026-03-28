## 1. Exclusion configuration and matching

- [x] 1.1 Add `NEXTDNS_REWRITE_EXCLUSIONS` environment loading plus a dedicated JSON config model/parser with fail-fast validation and a default for `cleanupExisting`.
- [x] 1.2 Implement a normalized wildcard domain matcher for NextDNS exclusions that supports `*`, lowercases domains, and applies the same leading-`www.` normalization used by the redirect pipeline.

## 2. NextDNS rewrite reconciliation

- [x] 2.1 Wire the exclusion config through `NextDnsTaskRunner` and `NextDnsRewriteService`, filtering matching redirect candidates before `CreateRewriteDto` requests are built.
- [x] 2.2 Extend rewrite cleanup so existing rewrites matching exclusion patterns are selected for deletion through the existing rate-limited API path, while unchanged and changed-IP non-excluded rewrites keep their current behavior.
- [x] 2.3 Add only the minimal tests/checks needed to prove config parsing, wildcard matching, excluded-create suppression, and `cleanupExisting=false` behavior, using representative GeoHideDNS data and exclusion patterns like `*.instagram.com` and `*.facebook.com`.

## 3. Validation and operator setup

- [x] 3.1 Add only the minimal `src/test` structure and Maven preview-feature test support required for those checks.
- [x] 3.2 Add repository-managed Docker validation entrypoints so build and test verification run only inside Docker and do not require host Java or Maven installation.
- [x] 3.3 Add placeholder configuration files and workflow wiring for `NEXTDNS_REWRITE_EXCLUSIONS`, clearly separating GitHub Environment variables from GitHub Environment secrets.
- [x] 3.4 Update `README.md` and `README.ru.md` with JSON examples, wildcard behavior notes, cleanup-safety notes, and Docker-based verification instructions.
- [x] 3.5 Add a Docker-based local apply entrypoint that reads `.env.local` and can run the real NextDNS sync from the workstation without host Java or Maven.

## 4. Verification

- [x] 4.1 Run the minimal verification flow through Docker only, confirm excluded domains are filtered while non-excluded domains pass through, and confirm the packaged build still succeeds with preview features enabled.
