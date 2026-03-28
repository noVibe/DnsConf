🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

# DNS Block&Redirect Configurer
**Allows to set Redirect and Block rules to your Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](#step-by-step-video-guide-redirect-for-nextdns)

[General comparison: Cloudflare vs NextDNS](#cloudflare-vs-nextdns)

[Setup credentials: Cloudflare](#cloudflare-credentials-setup)

[Setup credentials: NextDNS](#nextdns-credentials-setup)

[Setup profile](#setup-profile)

[Setup data sources](#setup-data-sources)

[GitHub Actions](#github-actions-setup)

## Cloudflare vs NextDNS

Both providers have free plans, but there are some limitations

### Cloudflare limitations
+ 100 000 DNS requests per day
+ Ipv4 DNS requests are restricted by the only one IP. But you are free to use other methods: DoH, DoT, Ipv6
### NextDNS limitations
+ 300 000 DNS requests per month (still more than enough for personal use)
+ Slow API speed is restricted by 60 requests per minute. Takes significantly more time for script to save settings

### Cloudflare credentials setup
1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.
- Free Plan has decent limits, so just choose it.
- Skip providing payment method step by choosing _Cancel and exit_ (top right corner)
- Go back to _Zero Trust_ tab

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

### NextDNS credentials setup
1) Generate **API KEY**, from https://my.nextdns.io/account and set as **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`


## Setup profile
Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

## Setup data sources
Each data source must be a link to a hosts file, e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Setup Redirects
Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.


+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.


### 2) Setup Blocklist
Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0` and `127.0.0.1`.

Thus, parsing lines

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `domain.to.block` and `another.to.block` for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the _Privacy_ tab.

### 3) Setup NextDNS rewrite exclusions (optional)
Set JSON config to **environment variable** `NEXTDNS_REWRITE_EXCLUSIONS`

```json
{"patterns":["*.instagram.com","*.facebook.com"]}
```

- `patterns` filters matching domains out of new NextDNS rewrite creation.
- This config is evaluated during the NextDNS `REDIRECT` rewrite flow.
- `cleanupExisting` controls only deletion of already existing matching rewrites in NextDNS.
- If `cleanupExisting` is omitted, it defaults to `false`.
- If `cleanupExisting=false`, new matching rewrites are still filtered out, but existing matching rewrites are left untouched.
- If no `REDIRECT` sources are provided, exclusion filtering and exclusion cleanup do not run.
- Matching is case-insensitive and strips leading `www.` before comparison.
- For convenience, a leading wildcard like `*.instagram.com` matches both `instagram.com` and its subdomains.
- If the JSON is invalid, the run fails with a clear configuration error.

## Script Behaviour
### Cloudflare
Previously generated data will be removed. Script recognizes old data by marks:


+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.


After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related **environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related data: lists and rules used to setup blocks.

### NextDNS

For `REDIRECT`:
+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched
+ Domains matching `NEXTDNS_REWRITE_EXCLUSIONS.patterns` are skipped from new rewrite creation

For `BLOCK`:
+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

For `NEXTDNS_REWRITE_EXCLUSIONS`:
+ If `cleanupExisting=true`, existing matching rewrites are removed through the existing rate-limited API path
+ If `cleanupExisting=false` or the field is omitted, existing matching rewrites stay as-is

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

## Docker-based validation

You do not need Java or Maven on the host machine.

Run local validation only through Docker:

```bash
docker compose run --rm validate
```

This runs `mvn -B clean test package` inside the container and verifies the minimal exclusion-filtering tests plus the packaged build.

Use `.env.example` as the placeholder for values. If you want a local non-committed copy, duplicate it to `.env.local`.

## Local apply through Docker

If you want to apply real changes to NextDNS from your own machine, you can run the application through Docker as well.

1. Create a local non-committed env file:

```bash
cp .env.example .env.local
```

2. Fill `.env.local` with your real values.
3. I recommend the first run with:

```json
{"patterns":["*.instagram.com","*.facebook.com"],"cleanupExisting":false}
```

4. Run the real apply flow:

```bash
docker compose --profile apply run --rm apply
```

This command builds the jar inside the container and then runs it with your `.env.local` values, so it can modify your live NextDNS profile.

What to watch in the logs:
- `Loaded ... NextDNS rewrite exclusion patterns. Existing cleanup: ...`
- `Skipping ... rewrite candidates due to exclusion patterns`
- `Removing ... excluded rewrites from NextDNS` when `cleanupExisting=true`
- `Saving ... new rewrites to NextDNS...`

Important:
- There is no dry-run mode here.
- `docker compose run --rm validate` is the safe check.
- `docker compose --profile apply run --rm apply` is the real mutation command.
- Exclusion filtering and exclusion cleanup are part of the `REDIRECT` rewrite flow, so they only run when `REDIRECT` sources are configured.

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`, `REDIRECT`, `BLOCK` and optional `NEXTDNS_REWRITE_EXCLUSIONS` to **Environment variables**

+ The action will be launched every day at **01:30 UTC**. To set another time, change cron at `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS Block&Redirect Configurer cron task**
