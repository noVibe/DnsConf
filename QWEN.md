# DNS Block&Redirect Configurer - Project Context

## Project Overview

**DNS Block&Redirect Configurer** is a TypeScript CLI tool that automates the configuration of DNS block and redirect rules for **Cloudflare** and **NextDNS** providers. The application is designed to run as a scheduled task via GitHub Actions, parsing hosts files from remote sources and applying blocking/redirect rules to the configured DNS provider.

### Key Features
- Supports **Cloudflare Zero Trust** and **NextDNS** providers
- Parses standard hosts file format from remote URLs
- Separates block rules (0.0.0.0/127.0.0.1) from redirect rules (custom IPs)
- Automated execution via GitHub Actions (daily at 01:30 UTC)
- Session-based cleanup of previously applied rules (Cloudflare)
- Incremental updates with rate limiting handling (NextDNS)
- Full TypeScript type safety

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Runtime** | Node.js 22+ |
| **Language** | TypeScript 5.x |
| **Modules** | ES Modules (NodeNext) |
| **HTTP Client** | node-fetch 3.x |
| **Environment** | dotenv |
| **CI/CD** | GitHub Actions |

## Project Structure

```
.
├── src/
│   ├── app.ts                              # Application entry point
│   ├── config/
│   │   └── environmentVariables.ts         # Environment variable configuration
│   ├── dataSources/
│   │   ├── hostsBlockListsLoader.ts        # Block list parser
│   │   └── hostsOverrideListsLoader.ts     # Redirect list parser
│   ├── dnsProviders/
│   │   ├── cloudflare/
│   │   │   ├── types.ts                    # Cloudflare API types
│   │   │   ├── cloudflareClient.ts         # Cloudflare API client
│   │   │   └── cloudflareTaskRunner.ts     # Cloudflare implementation
│   │   └── nextDns/
│   │       ├── types.ts                    # NextDNS API types
│   │       ├── nextDnsClient.js            # NextDNS API client
│   │       ├── nextDnsRateLimitedApiProcessor.ts  # Rate limiting handler
│   │       └── nextDnsTaskRunner.ts        # NextDNS implementation
│   └── utils/
│       ├── envParser.ts                    # Environment variable parsing
│       └── log.ts                          # Colored console logging
├── dist/                                   # Compiled JavaScript output
├── .env                                    # Environment variables (gitignored)
├── .env.example                            # Example environment file
├── .gitignore
├── package.json
├── tsconfig.json                           # TypeScript configuration
└── README.md
```

## Building and Running

### Prerequisites
- **Node.js 22+** (required)
- **npm** (comes with Node.js)

### Install Dependencies

```bash
npm install
```

### Configure Environment

Copy `.env.example` to `.env` and fill in your credentials:

```bash
cp .env.example .env
# Edit .env with your values
```

### Build TypeScript

```bash
npm run build
```

### Run the Application

```bash
npm start
```

### Development Mode

```bash
npm run dev
```

### Type Checking

```bash
npm run typecheck
```

### Required Environment Variables

| Variable | Description | Mandatory |
|----------|-------------|-----------|
| `DNS` | Provider name: `CLOUDFLARE` or `NEXTDNS` | Yes |
| `CLIENT_ID` | Cloudflare Account ID or NextDNS Configuration ID | Yes |
| `AUTH_SECRET` | Cloudflare API Token or NextDNS API Key | Yes |
| `BLOCK` | Comma-separated URLs to hosts files for blocking | No* |
| `REDIRECT` | Comma-separated URLs to hosts files for redirects | No* |

*At least one of `BLOCK` or `REDIRECT` should be provided for meaningful execution.

### Example Local Execution

```bash
# Set environment variables in .env file
DNS=CLOUDFLARE
CLIENT_ID=your_account_id
AUTH_SECRET=your_api_token
REDIRECT=https://example.com/hosts

npm run build
npm start
```

## Architecture

### Design Pattern
The application uses a **Strategy Pattern** with ES Modules:
- Each provider has its own `TaskRunner` class
- Provider selection is done at runtime based on `DNS` environment variable
- Shared utilities for logging, parsing, and HTTP requests
- Full TypeScript type definitions for API responses and requests

### Data Flow
1. **Parse hosts files** from remote URLs using node-fetch
2. **Filter entries**:
   - Block: lines starting with `0.0.0.0` or `127.0.0.1`
   - Redirect: lines with custom IP addresses
3. **Clean old data** (Cloudflare: by session markers; NextDNS: incremental)
4. **Apply new rules** via provider API

### Provider-Specific Behavior

| Aspect | Cloudflare | NextDNS |
|--------|------------|---------|
| **Cleanup** | Always removes old rules/lists before applying new ones | Incremental updates; full cleanup only when no sources provided |
| **Rate Limiting** | Standard API limits | 60 requests/minute with automatic retry |
| **Session Tracking** | Session ID stored in description field | N/A |
| **API Auth** | Bearer token in Authorization header | X-Api-Key header |

## Development Conventions

### Code Style
- **TypeScript** with strict mode enabled
- **ES Modules** (`import`/`export`)
- **Async/await** for all asynchronous operations
- **Functional programming** patterns for data transformation
- **Named exports** for utilities, **default exports** for main classes
- **Explicit type annotations** for function parameters and return types

### Logging
Custom `log.ts` utility provides colored console output:
- `global()` - Major section headers (yellow/green)
- `step()` - Step headers (blue)
- `io()` - I/O operations (yellow/purple)
- `fail()` - Error messages (red)
- `progress()` - Progress updates (carriage return)

### Error Handling
- Application exits with code 1 on critical errors
- Mandatory environment variables validated at startup
- Graceful handling of empty block/redirect sources
- NextDNS rate limiting handled automatically with retry logic
- TypeScript strict mode catches type errors at compile time

## GitHub Actions Configuration

Workflow: `.github/workflows/github_action.yml`

- **Schedule**: Daily at 01:30 UTC (04:30 MSK)
- **Manual trigger**: Available via `workflow_dispatch`
- **Environment**: `DNS` (configured in repository settings)

### Setup Steps
1. Fork repository
2. Go to Settings → Environments
3. Create environment named `DNS`
4. Add secrets: `AUTH_SECRET`, `CLIENT_ID`
5. Add variables: `DNS`, `BLOCK`, `REDIRECT`

## Key Implementation Details

### Hosts File Parsing
```
Format: <IP> <domain>
- 0.0.0.0 domain.com     → Block
- 127.0.0.1 domain.com   → Block
- 1.2.3.4 domain.com     → Redirect to 1.2.3.4
```

### Redirect Priority
When a domain appears in multiple sources, the **first occurrence** wins (source order matters).

### Session Management (Cloudflare)
Old data is identified by:
- List name prefix: `Blocked websites by script` / `Override websites by script`
- Rule name prefix: `Rules set by script`
- Session ID in description field

### Rate Limiting (NextDNS)
- API limited to 60 requests per minute
- Automatic retry with 60-second backoff on 429/524 errors
- Progress tracking during batch operations

## npm Scripts

```bash
npm run build       # Compile TypeScript to JavaScript
npm start           # Run compiled application from dist/
npm run dev         # Run TypeScript directly (requires ts-node)
npm run typecheck   # Type check without emitting files
```

## Related Files

- `README.md` - English user documentation
- `README.ru.md` - Russian user documentation
- `package.json` - npm package configuration
- `tsconfig.json` - TypeScript compiler configuration
- `.github/workflows/github_action.yml` - CI/CD pipeline
- `.env` - Environment variables (gitignored)
- `.env.example` - Example environment file template
