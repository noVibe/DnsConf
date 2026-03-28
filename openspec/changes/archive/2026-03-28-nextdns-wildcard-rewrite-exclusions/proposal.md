## Why

The NextDNS flow currently accepts redirect sources from `REDIRECT`, builds exact-domain rewrites, and only deletes rewrites whose IP changed. That leaves no way to declare wildcard-based exclusions from GitHub Environment variables and no safe reconciliation path for existing rewrites that should now be excluded. The repository also has no Docker-based validation path, which makes this change harder to verify on machines without a local Java setup.

## What Changes

- Add a new NextDNS rewrite-exclusion configuration sourced from a GitHub Environment variable containing JSON-defined wildcard domain patterns.
- Prevent redirect candidates that match configured exclusion patterns from being turned into `CreateRewriteDto` requests.
- Reconcile existing NextDNS rewrites against the exclusion patterns and delete only matching rewrites through the existing rate-limited API path instead of doing unrestricted cleanup.
- Document the new configuration, including where to place GitHub Environment variables and secrets, plus safe placeholder values/examples for local or CI-driven setup.
- Add a Docker-based validation workflow so testing and verification can run without requiring Java to be installed on the host machine.

## Capabilities

### New Capabilities
- `nextdns-rewrite-exclusions`: Configure wildcard domain exclusions for NextDNS rewrites, skip matching rewrite creation, and safely remove matching existing rewrites during reconciliation.
- `containerized-validation`: Provide Docker-based validation entrypoints and configuration placeholders so the project can be tested and exercised without a host Java installation.

### Modified Capabilities
- None.

## Impact

- Affected code: `src/main/java/com/novibe/common/config/EnvironmentVariables.java`, `src/main/java/com/novibe/common/util/EnvParser.java`, `src/main/java/com/novibe/dns/next_dns/NextDnsTaskRunner.java`, `src/main/java/com/novibe/dns/next_dns/service/NextDnsRewriteService.java`, and likely a new wildcard-matching/config utility.
- Affected automation and docs: `.github/workflows/github_action.yml`, `README.md`, `README.ru.md`, and new Docker/test helper files.
- External systems: GitHub Environment variables/secrets and NextDNS rewrite API behavior, including cautious handling of undocumented-but-observed API throttling.
