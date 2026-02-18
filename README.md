🌐 Languages: [**English**](README.md) | [**Русский**](README.ru.md)

# DNS Block&Redirect Configurer

**Automates DNS block and redirect rules for Cloudflare and NextDNS providers.**

**Ready-to-run via GitHub Actions or locally with Node.js 22+.** [Video guide](#step-by-step-video-guide-redirect-for-nextdns)

## Quick Start

### Local Usage

```bash
# Clone and install
git clone <repository-url>
cd dns-block-and-redirect-configurer
npm install

# Configure environment
cp .env.example .env
# Edit .env with your credentials

# Run
npm run build
npm start
```

### GitHub Actions

Automatically runs daily at 01:30 UTC. See [setup instructions](#github-actions-setup).

---

## Table of Contents

- [Cloudflare vs NextDNS](#cloudflare-vs-nextdns)
- [Credentials Setup](#credentials-setup)
- [Configuration](#configuration)
- [Data Sources](#data-sources)
- [Script Behavior](#script-behavior)
- [GitHub Actions](#github-actions-setup)
- [Local Development](#local-development)

---

## Cloudflare vs NextDNS

Both providers offer free plans with some limitations:

### Cloudflare Limitations
- 100,000 DNS requests per day
- IPv4 DNS requests restricted to one IP (DoH, DoT, IPv6 are unlimited)

### NextDNS Limitations
- 300,000 DNS requests per month (sufficient for personal use)
- API rate limited to 60 requests per minute (slower execution)

---

## Credentials Setup

### Cloudflare

1. **Create Zero Trust Account:**
   - Sign up at Cloudflare, navigate to _Zero Trust_ tab
   - Choose Free Plan
   - Skip payment method (_Cancel and exit_)

2. **Create API Token:** https://dash.cloudflare.com/profile/api-tokens
   
   Required permissions:
   - `Account.Zero Trust : Edit`
   - `Account.Account Firewall Access Rules : Edit`
   
   Set token to environment variable `AUTH_SECRET`

3. **Get Account ID:** https://dash.cloudflare.com/?to=/:account/workers
   
   Set to environment variable `CLIENT_ID`

### NextDNS

1. **Generate API Key:** https://my.nextdns.io/account
   
   Set to environment variable `AUTH_SECRET`

2. **Get Configuration ID:**
   - Click NextDNS logo
   - Copy ID from _Endpoints_ section
   
   Set to environment variable `CLIENT_ID`

---

## Configuration

Create a `.env` file or set environment variables:

```bash
# Required
DNS=CLOUDFLARE                    # or NEXTDNS
CLIENT_ID=your_account_id
AUTH_SECRET=your_api_token

# Optional
BLOCK=https://example.com/hosts   # Comma-separated URLs
REDIRECT=https://example.com/hosts # Comma-separated URLs
```

---

## Data Sources

Each source must be a URL to a hosts file format.

Example: `https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts`

Multiple sources: `https://first.com/hosts,https://second.com/hosts`

### Block Rules (`BLOCK`)

Parses hosts files, keeping only lines starting with `0.0.0.0` or `127.0.0.1`:

```
0.0.0.0 domain.to.block      → blocks domain.to.block
127.0.0.1 another.to.block   → blocks another.to.block
1.2.3.4 domain.to.redirect   → ignored
```

### Redirect Rules (`REDIRECT`)

Parses hosts files, keeping only custom IP redirects:

```
1.2.3.4 domain.to.redirect   → redirects to 1.2.3.4
0.0.0.0 domain.to.block      → ignored
127.0.0.1 another.to.block   → ignored
```

**Note:** Redirect priority follows source order. First occurrence wins.

### Provider Recommendations

- **Cloudflare:** Can use same source for both `BLOCK` and `REDIRECT`
- **NextDNS:** Use `REDIRECT` only, select blocklists manually in _Privacy_ tab

---

## Script Behavior

### Cloudflare

- **Always removes old data** before applying new rules
- Identifies old data by:
  - List prefix: `Blocked websites by script` / `Override websites by script`
  - Rule prefix: `Rules set by script`
  - Session ID in description field

To clear all Cloudflare settings: run without `BLOCK` or `REDIRECT` values.

### NextDNS

**For `REDIRECT`:**
- Updates existing domains if redirect IP changed
- Adds new domains
- Preserves other settings

**For `BLOCK`:**
- Adds new domains
- Preserves existing settings

**Full cleanup:** Only when both `BLOCK` and `REDIRECT` are empty.

---

## GitHub Actions Setup

### Step-by-step Video: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

### Steps

1. **Fork repository**

2. **Create Environment:**
   - Go to _Settings_ → _Environments_
   - Create new environment: `DNS`

3. **Add Secrets:**
   - `AUTH_SECRET` - API token/key
   - `CLIENT_ID` - Account/Configuration ID

4. **Add Variables:**
   - `DNS` - Provider name
   - `BLOCK` - Blocklist sources (optional)
   - `REDIRECT` - Redirect sources (optional)

### Schedule

- Runs daily at **01:30 UTC** (04:30 MSK)
- Change schedule in `.github/workflows/github_action.yml`
- Manual trigger available via _Actions_ tab → _Run workflow_

---

## Local Development

### Prerequisites

- Node.js 22+
- npm

### Setup

```bash
npm install
cp .env.example .env
# Edit .env with your values
```

### Commands

```bash
npm run build       # Compile TypeScript
npm start           # Run application
npm run typecheck   # Type check only
```

---

## Tech Stack

- **Runtime:** Node.js 22+
- **Language:** TypeScript 5.x
- **HTTP Client:** node-fetch
- **CI/CD:** GitHub Actions

---

## License

ISC
