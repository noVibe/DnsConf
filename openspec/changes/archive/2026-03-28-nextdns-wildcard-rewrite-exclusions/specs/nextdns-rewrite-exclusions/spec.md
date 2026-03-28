## ADDED Requirements

### Requirement: NextDNS rewrite exclusions SHALL be configurable from environment JSON
The system SHALL support an optional `NEXTDNS_REWRITE_EXCLUSIONS` environment variable for the NextDNS flow. When present, the value MUST be parsed as JSON containing wildcard rewrite exclusion settings. When absent or blank, the NextDNS flow MUST continue with existing behavior. When the JSON is malformed, the run MUST fail with a clear configuration error instead of silently ignoring the value.

#### Scenario: Exclusion config is present and valid
- **WHEN** `NEXTDNS_REWRITE_EXCLUSIONS` contains valid JSON with one or more patterns
- **THEN** the NextDNS flow loads those patterns and uses them during rewrite reconciliation

#### Scenario: Exclusion config is absent
- **WHEN** `NEXTDNS_REWRITE_EXCLUSIONS` is unset or blank
- **THEN** the NextDNS flow behaves as if no rewrite exclusions were configured

#### Scenario: Exclusion config is malformed
- **WHEN** `NEXTDNS_REWRITE_EXCLUSIONS` contains invalid JSON
- **THEN** the run fails with an explicit configuration error before rewrite mutations begin

### Requirement: Matching domains SHALL not produce new NextDNS rewrites
The system SHALL evaluate loaded redirect domains against the configured exclusion patterns before creating NextDNS rewrite requests. Domains that match an exclusion pattern MUST be omitted from the desired rewrite set, while domains that do not match any exclusion pattern MUST continue through the existing rewrite creation flow.

#### Scenario: Redirect candidate matches wildcard exclusion
- **WHEN** a parsed redirect domain matches one of the configured exclusion patterns
- **THEN** no `CreateRewriteDto` is produced for that domain

#### Scenario: Redirect candidate does not match wildcard exclusion
- **WHEN** a parsed redirect domain does not match any configured exclusion pattern
- **THEN** the domain remains eligible for normal NextDNS rewrite creation or update handling

### Requirement: Existing excluded rewrites SHALL be cleaned up safely
The system SHALL compare existing NextDNS rewrites against the configured exclusion patterns and, when cleanup is enabled, delete matching rewrites through the existing rate-limited NextDNS API path. When the API signals throttling through responses recognized by the central rate-limit processor, cleanup MUST wait and retry according to that processor behavior instead of requiring a user-configured delete limit.

#### Scenario: Matching existing rewrite is deleted when cleanup is enabled
- **WHEN** an existing NextDNS rewrite matches an exclusion pattern and cleanup is enabled
- **THEN** the rewrite is selected for deletion through the NextDNS rate-limited delete path

#### Scenario: Cleanup waits and retries on rate limiting
- **WHEN** NextDNS deletion of an excluded rewrite hits a throttling response recognized by the central rate-limit processor
- **THEN** the cleanup waits and retries through that processor instead of failing immediately or requiring a manual deletion cap

#### Scenario: Cleanup can be disabled without disabling exclusion filtering
- **WHEN** exclusion patterns are configured and cleanup is explicitly disabled
- **THEN** matching redirect candidates are still excluded from new rewrite creation but existing matching rewrites are not deleted

### Requirement: Non-excluded rewrite reconciliation SHALL preserve current behavior
The system SHALL keep the current NextDNS rewrite update behavior for domains that are not excluded. Existing rewrites with the same domain and unchanged IP MUST not be recreated, and existing rewrites with the same domain but a changed IP MUST still be deleted and recreated.

#### Scenario: Existing non-excluded rewrite already matches desired state
- **WHEN** an existing rewrite domain is not excluded and already has the desired IP
- **THEN** no delete or create request is issued for that domain

#### Scenario: Existing non-excluded rewrite needs IP update
- **WHEN** an existing rewrite domain is not excluded and the desired IP differs from the current IP
- **THEN** the old rewrite is deleted and a replacement rewrite is created
