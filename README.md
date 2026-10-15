🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>

# DNS Block&Redirect Configurer

**Allows to set Redirect and Block rules to your Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](https://www.youtube.com/watch?v=vbAXM_xAL5I)

## Comparison of Free Plans: NextDNS vs Cloudflare

|                                  | NextDNS                                                         | Cloudflare                                                                                                           |
|----------------------------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **DNS Query Limit**              | 300,000 per month                                               | 100,000 per day                                                                                                      |
| **Billing information required** | No                                                              | Yes                                                                                                                  |
| **IPv4 Restrictions**            | DNS queries are limited to a single IP address (can be changed) | DNS queries are strictly limited to a single IP address (automatically assigned by Cloudflare and cannot be changed) |
| **DoH / DoT / IPv6**             | Unlimited                                                       | Unlimited                                                                                                            |
| **Setup API Limits**             | 60 requests per minute                                          | Unlimited                                                                                                            |
| **General Limitations**          | None                                                            | Infrastructure is blocked by Roskomnadzor (availability issues in Russia)                                            |
| **Advantages**                   | Built-in ad and tracker blocking options                        | More reliable and fast infrastructure                                                                                |

In summary: 
- If you are located in Russia, **NextDNS** is your only viable option due to Roskomnadzor restrictions.
- If you are in other country than Russia, and you're ok with a credit card gate, **Cloudflare** offers more generous limits on the free plan. 
Tracker and ad blocking can also be enabled by providing a domain blocklist in `BLOCK`, for example: https://small.oisd.nl/domainswild2

## Easy Setup

Use the configurator https://dns-conf-ui.vercel.app in **Quick** mode. It will automatically configure your DNS profile
and perform all required GitHub setup steps.

You only need to sign in with GitHub and provide **CLIENT_ID** and **AUTH_SECRET**. More details on where to find these
values here: [Setup credentials](#setup-credentials)

## Standard Setup

[Set up credentials](#set-up-credentials)

[Set up profile](#set-up-profile)

[Set up data sources](#set-up-data-sources)

[Limit redirects to a source section](#limit-redirects-to-a-source-section-optional)

[Set up exclude redirects (optional)](#set-up-exclude-redirects-optional)

[Set up a DNS donor](#set-up-a-dns-donor)

[Multiple profiles setup](#multiple-profiles-setup)

[GitHub Actions setup](#github-actions-setup)

---

## Set up credentials

### NextDNS credentials setup

1) Generate an **API KEY** from https://my.nextdns.io/account and set it as an **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`

### Cloudflare credentials setup

1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

---

## Set up profile

Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

---

## Set up data sources

Each data source must be a link to a hosts file,
e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Set up Redirects

Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.

+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.

### 2) Set up Blocklist

Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0`, `127.0.0.1`, `::1`, and also lines containing domain
only.

Thus, parsing lines

    1.2.3.4 domain.to.redirect
    0.0.0.0 domain.to.block
    127.0.0.1 another.to.block
    ::1 ipv6.to.block
    no-ip.just.domain

will keep only

    domain.to.block
    another.to.block
    no-ip.just.domain
    ipv6.to.block

for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the
  _Privacy_ tab.

---

## Limit redirects to a source section (optional)

Set the `REDIRECT_SECTION` environment variable to a section heading without `#`, for example `Google Gemini AI`
for the [GeoHideDNS hosts file](https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts).
This fork's scheduled workflow uses `Google Gemini AI` by default; the `REDIRECT_SECTION` variable in the `DNS`
environment can override it.
Only host entries between `# Google Gemini AI` and the next heading are used. Every `REDIRECT` source must contain
the section; the run stops before changing rewrites if it is missing or contains no valid redirects.

With `REDIRECT_SECTION` set, NextDNS removes existing rewrites outside the selected source entries, including
manually added rewrites. Without it, existing rewrites outside the source remain untouched.

---

## Set up exclude redirects (optional)

Put domains to **environment variable** `EXCLUDE_REDIRECT` separated by coma, e.g. `instagram.com,twitch.com`

These domains and their subdomains:

- will be removed from existing redirect rules;
- won't be added with new ones.

---

## Set up a DNS donor

If IP addresses of domains are outdated, they can be updated via "donor" DNS. You need:

1) Create **environment variable** `DONOR_DNS`
2) Provide the **DNS-provider** that will be used as a **donor**.
   Use one of the following formats:

- **IPv4** (e.g. `111.88.96.50`)
- **DoH** (e.g. `https://xbox-dns.ru/dns-query`)

For instance, if your hosts-file supplies following:

    1.2.3.4 domain-1.to.redirect
    1.2.3.4 domain-2.to.redirect
    1.2.3.4 domain-3.to.redirect

Then, a `DONOR_DNS` value will be called for each domain, fetching new IP addresses:

    5.6.7.8 domain-1.to.redirect
    2.3.4.5 domain-2.to.redirect
    9.8.7.6 domain-3.to.redirect

Thus, the new IPs will be used for the further redirect rules upload process.

---

## Multiple profiles setup

### Restrictions

All profiles get _similar_ settings. That means `BLOCK`, `REDIRECT`, `REDIRECT_SECTION` and `EXCLUDE_REDIRECT` are **shared**.

### Multiple profiles of single provider

Put your profiles separated by coma into related **environment variables**.
E.g., two NextDNS profiles must be set as shown:

- `AUTH_SECRET` has: `secret_NextDns_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_NextDns_2`

### Multiple profiles of different providers

In addition to setting above, list provider for each profile in **environment variable** `DNS`. For example:

- `DNS` has: `NEXTDNS,CLOUDFLARE,NEXTDNS`
- `AUTH_SECRET` has: `secret_NextDns_1,secret_Cloudflare_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_Cloudflare_1,client_id_NextDns_2`

---

## Script Behavior

### Cloudflare

Previously generated data will be removed. Script recognizes old data by marks:

+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.

After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related *
*environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related
data: lists and rules used to set up blocks.

### NextDNS

For `REDIRECT`:

+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched unless `REDIRECT_SECTION` is set

For `BLOCK`:

+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

---

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`,`REDIRECT`, `BLOCK`, `REDIRECT_SECTION` and `EXCLUDE_REDIRECT` to **Environment variables**

+ The action will be executed every day at **01:30 UTC**. To set another time, change cron at
  `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS
  Block&Redirect Configurer cron task**

---

## Keep the action running

GitHub disables scheduled workflows in a public repository
[if there has been no activity in it for 60 days](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
Your fork is a public repository. **A scheduled run is not counted as activity**. Therefore, approximately once every two months
you need to run it manually.

**How to check:** Click the badge below (the same one is at the top of the README) to go to the run history.<br>
[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>
A disabled workflow will have a notice that scheduled runs are turned off. In the run history (workflow runs), you can see the date of the last run.

**How to turn it on again:**
Click the badge above and press the Run Workflow button<br>
_Or_: _Actions_ tab → workflow **DNS Block&Redirect Configurer cron task** → **Enable workflow** button.<br>
Reconfiguring via the configurator https://dns-conf-ui.vercel.app also enables the workflow.

Keep in mind that proxy IPs change over time, so in a fork that has been disabled for a long time, the rules remain
outdated until the first successful run.
