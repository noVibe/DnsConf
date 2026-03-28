## ADDED Requirements

### Requirement: The repository SHALL provide Docker-only validation without host Java or Maven
The repository SHALL provide a documented Docker-based validation entrypoint that runs the project’s verification steps without requiring Java or Maven to be installed on the host machine. The containerized validation path MUST be suitable for routine development verification of this change.

#### Scenario: Developer runs validation in Docker
- **WHEN** a developer follows the documented containerized validation command
- **THEN** the project build and test verification run inside Docker rather than relying on host Java or Maven installation

### Requirement: Validation scope SHALL stay minimal and cover exclusion filtering behavior
The repository SHALL provide a minimal validation scenario that proves wildcard exclusions work on representative hosts data without introducing a broad or heavy test matrix.

#### Scenario: Minimal validation filters excluded domains
- **WHEN** Docker-based validation runs against representative hosts data from the GeoHideDNS source with exclusion patterns such as `*.instagram.com` and `*.facebook.com`
- **THEN** matching domains are filtered out and non-matching domains remain eligible for further processing

### Requirement: Validation setup SHALL support Java preview features in tests
The repository SHALL configure its test execution path so Java preview features used by the project can be compiled and exercised during containerized validation.

#### Scenario: Tests execute against preview-feature code
- **WHEN** containerized validation runs Maven tests for code that depends on Java preview features
- **THEN** the test execution is configured with preview support and does not fail solely because preview flags are missing

### Requirement: Placeholder configuration SHALL distinguish variables from secrets
The repository SHALL provide placeholder configuration and documentation for the inputs needed by this project, including the new `NEXTDNS_REWRITE_EXCLUSIONS` variable. The placeholders MUST distinguish non-secret environment variables from secret values so users know where to place each input in GitHub Environments and in local Docker-based setup.

#### Scenario: User prepares GitHub environment inputs
- **WHEN** a user reads the placeholder configuration for this repository
- **THEN** they can identify which values belong in GitHub Environment variables and which values belong in GitHub Environment secrets

#### Scenario: User prepares local optional runtime configuration
- **WHEN** a user wants to provide local values for Docker-based execution
- **THEN** the repository gives them a placeholder file or documented insertion point where they can add their own non-committed values
