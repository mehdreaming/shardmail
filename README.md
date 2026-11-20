# ShardMail — Free Custom-Domain Email Inbox for Android

> **$0/month. No server. Set up the whole thing from any browser.**
> A native Android inbox app for your own custom domain, powered by a serverless
> Cloudflare Worker + D1 database. You can set up the whole thing from any browser.

With ShardMail you get real email addresses on your own domain — like
`anything@yourdomain.com` — for free. Mail arrives in the Android app, verification
codes get a one-tap copy button, and everything works offline thanks to a local cache.

---

## Features

- **Free custom-domain email** — receive mail at any address on your domain.
- **One-tap OTP copy** — 6-digit verification codes are detected automatically and
  shown as a "Copy 123456" chip right on the inbox row.
- **Aliases on the fly** — create, copy, and delete addresses like `shop@`, `bank@`,
  `test@` from the app, with a per-account inbox and mail count.
- **Bulk delete** — long-press a mail to select several, then delete them at once.
- **Offline cache** — your last mail stays readable with no network; the app retries
  automatically when the connection returns.
- **Encrypted keys** — domain, Worker URL, and passwords are stored only on your
  device (Android Keystore / EncryptedSharedPreferences). Nothing is hardcoded.
- **Material You design** — M3 Expressive look, navigation drawer, bottom navigation,
  dark snackbar, empty states, smooth motion throughout.
- **First-run onboarding** — greeting → setup keys → loading → inbox. Setup shows
  only the very first time.

---

## How it works

```
Anyone sends mail to            Cloudflare Email Routing
anything@yourdomain.com   --->  Catch-all rule
                                     |
                                     v
                          Cloudflare Worker (shardmail-worker)
                          - parses the mail, extracts the OTP code
                          - stores it in D1 (shardmail-db)
                                     |
                                     v  HTTPS API
                          ShardMail Android app
                          - Inbox / Accounts / Keys
                          - offline cache, OTP copy, bulk delete
```

There is no server to rent and nothing running locally. Cloudflare's
free tier covers all of it.

---

## What you need (all free)

- An Android device to run the app.
- A free domain (Part 1).
- A free Cloudflare account (Part 2).
- A free GitHub account (Part 3, for the one-tap deploy).

---

## Part 1 — Get a free domain (dnshe.com)

You need a domain before anything else. A free one from dnshe is enough.

1. Open **https://my.dnshe.com** in a browser.
2. Create an account (email + password) and sign in.
3. Find the free domain section and claim a free domain. The free option looks like
   `yourname.ccwu.cc` — pick any available name and register it. It costs $0.
4. Write down your full domain, for example `yourname.ccwu.cc`. You will type this
   into Cloudflare and into the app later.
5. Notes: a free `.ccwu.cc` domain is a free subdomain, not a domain you own — that
   is fine for this project. It stays free for years; renew it for free from the
   Domain Hub within 180 days before it expires, so it is never lost.

> Do not buy a domain for this. The free one works. If you already own a domain,
> you can use that instead — skip to Part 2.

---

## Part 2 — Add your domain to Cloudflare (free)

Cloudflare handles your domain's DNS and receives your mail.

1. Open **https://dash.cloudflare.com/sign-up** and create a free Cloudflare account.
2. Click **Add site**, type your domain (for example `yourname.ccwu.cc`), and choose
   the **Free** plan.
3. Cloudflare shows you **two nameservers**, for example
   `xxx.ns.cloudflare.com` and `yyy.ns.cloudflare.com`. Copy both.
4. Go back to **my.dnshe.com** → open your domain → **DNS Servers** (or
   "Nameservers") → choose **Custom / External** → paste the two Cloudflare
   nameservers → Save.
5. Back in Cloudflare, wait for the zone status to become **Active**. This usually
   takes a few minutes, sometimes a few hours. Until it says Active, mail cannot
   arrive yet — that is normal, wait and check again later.

---

## Part 3 — Deploy the backend from GitHub

The backend is one Cloudflare Worker plus one D1 database. You deploy it from
GitHub in about 5 minutes. No terminal needed.

### 3a. Fork this repository

1. On GitHub, open this repository and tap **Fork**.
2. You now have your own copy at `github.com/YOUR-USERNAME/shardmail`.

### 3b. Create a Cloudflare API token

The API token must have full read and write permissions to deploy your Worker and create/migrate your D1 database. If it doesn't, the Deploy Worker workflow fails with `Authentication error [code: 10000]` at the "Prepare D1 Database" step.

1. In the Cloudflare dashboard, tap your profile icon → **My Profile → API Tokens → Create Token**.
2. **Token name:** type a name, for example `ShardMail deploy`.
3. Under **Policies** you will see three blocks: **Edit Account policy**, **Edit Zone policy**, and **Edit User policy**.
   - **Edit Account policy:** set it to **Include → All accounts**.
   - **Edit Zone policy:** set it to **Include → Specific zone → select your domain** (for example `yourname.ccwu.cc`).
   - In each policy block, click **Bulk Select** (top right of the permissions list) → **Add all Read & Write permissions**. Do this for the Account policy, the Zone policy, and the User policy.
4. Continue to summary and click **Create Token**.
5. **Copy the token immediately** — Cloudflare shows it only once. This token lets GitHub deploy on your behalf; never share it or post it anywhere.

### 3c. Find your Cloudflare Account ID

While logged into the Cloudflare dashboard from any browser:
1. The Account ID is the long hex string shown directly in your browser URL:
   `dash.cloudflare.com/<account-id>/...`
2. It is also displayed on the dashboard home page and on the right-hand sidebar of the **Workers & Pages** overview under **Account ID**. Copy this string.

### 3d. Add three secrets to your fork

1. In your fork on GitHub: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**. Add each of these:
   - `CLOUDFLARE_API_TOKEN` — the token from step 3b.
   - `CLOUDFLARE_ACCOUNT_ID` — the ID from step 3c.
   - `ADMIN_TOKEN` — a strong password **you invent**. This is your app's admin password. Make it long and random (for example 24+ letters and numbers) and save it somewhere safe. You will type this same password into the app later.
2. Secrets are never shown again after saving and never appear in logs.

### 3e. Run the deploy workflow

1. In your fork: **Actions** tab → **Deploy Worker** workflow (left sidebar) →
   **Run workflow** → **Run workflow**.
2. Wait for the run to finish (about 1–2 minutes). It creates the D1 database,
   runs the migrations, deploys the Worker, and checks `/health`.
3. Tap the finished run and open the log. Near the end it prints your Worker URL:
   ```
   https://shardmail-worker.<your-subdomain>.workers.dev
   ```
   Copy this URL — you need it in the app.

> **Terminal alternative (optional):** if you prefer the terminal, you can deploy
> the same way with Node.js installed:
> `git clone` this repo → `cd shardmail/backend` → `npm install` →
> `npx wrangler login` → `npx wrangler d1 create shardmail-db` →
> `npx wrangler d1 migrations apply shardmail-db --remote` →
> `npx wrangler secret put ADMIN_TOKEN` (type the same password as in step 3d) →
> `npx wrangler deploy`. The result is identical.

---

## Part 4 — Connect Email Routing (catch-all)

This routes all incoming mail for your domain directly into the Worker.

> **Note:** The domain's zone must be **Active** in Cloudflare first (nameservers pointed at Cloudflare from Part 2), and mail only flows after the catch-all rule is **Active**.

1. In the Cloudflare dashboard, open your domain → **Email** → **Email Routing**.
2. Click **Onboard Domain** (or **Enable** / **Get started**). Select the zone (your domain, for example `yourname.ccwu.cc`) → **Continue**.
3. **Verify DNS records:** Cloudflare proposes the required MX and TXT records — review them and click **Activate** (or **Add records and enable**).
4. Once enabled, go to **Routing rules** → under **Catch-all**, click **Edit**.
5. Set the Action to **Send to a Worker** and select **`shardmail-worker`** → click **Save**.
6. **Check the rule shows Status: Active:** Ensure the toggle switch next to Catch-all is ON (`Active`). If the toggle is off, no mail arrives.
7. From now on, **every** address works — `test@`, `shop@`, `anything@` your domain. The aliases you create in the app are just names to keep track of them.

---

## Part 5 — Install the app

1. Open this repository on GitHub → **Releases** tab.
2. Download the latest APK (for example `ShardMail-app-debug.apk`) and install it.
   Your device will ask once to allow installs from the browser — allow it, it is
   your own app.
3. Or build it yourself: `cd android` → `./gradlew assembleDebug` (needs JDK 17
   and the Android SDK). The APK lands in
   `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## Part 6 — First run in the app

1. Open ShardMail. Greeting screen → tap **Get started**.
2. On **Setup API Keys**, fill in, in order:
   - **Your domain** — the domain from Part 1, for example `yourname.ccwu.cc`.
   - **Worker API URL** — the URL from Part 3e, for example
     `https://shardmail-worker.<your-subdomain>.workers.dev`.
   - **Admin Password** — the `ADMIN_TOKEN` password you invented in Part 3d.
   - **Resend API Key** and **Cloudflare API Token** — leave empty. Both are
     optional.
3. Tap **Test Connection**. It must say **Connected**. Then tap **Continue**.
4. A loading screen appears, then your inbox. Setup never shows again after this —
   if you ever need to change keys, use the **Keys** tab.

---

## Part 7 — Using ShardMail

- **New address:** tap **+ New** (bottom right) → type a name like `shop` → the
  full address `shop@yourdomain.com` is created and copied for you.
- **Receive mail:** send a test email from Gmail to one of your addresses. It
  appears in the inbox within seconds.
- **Copy a code:** any 6-digit verification code shows a **Copy 123456** chip on the
  mail row — one tap copies it.
- **Find mail:** search from the top bar; filter with **All / Unread / Has OTP**.
- **Per-account inbox:** open an account card and tap **Open inbox** to see only
  that address's mail.
- **Delete in bulk:** long-press any mail to select, tap more mails, then Delete.
- **Change keys later:** **Keys** tab → edit → **Save Keys** → **Test Connection**.

---

## Sending mail (optional, off by default)

Sending is off on purpose. The app is an inbox, not a sender. If you ever want
to send:

- **Resend** (the built-in option) rejects free public domains like `.ccwu.cc`,
  so it will not work with a free dnshe domain.
- Alternatives: Gmail's **"Send mail as"** setting, **Brevo** (free tier), or an
  owned domain + a Resend API key (enter the key in the **Keys** tab, and the
  send option appears).

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `Worker not reachable` / Error 1101 | Backend was never deployed | Run the **Deploy Worker** workflow again (Part 3e) and wait for it to finish |
| `Wrong admin password (401)` | `ADMIN_TOKEN` secret and app password don't match | Set both to the same new password: update the `ADMIN_TOKEN` repo secret, re-run the workflow, then type the same password in the app |
| `Worker URL not set` | URL field empty or still has `<subdomain>` in it | Paste the full Worker URL from the workflow log |
| Mail not arriving | Catch-all not active, or domain not Active | Check Part 4 (rule Active + worker selected) and Part 2 (zone Active) |
| Domain never becomes Active | Nameservers not updated at dnshe | Re-check Part 2 step 4 — the two nameservers must be exactly Cloudflare's |
| Test Connection works but inbox is empty | Nothing sent yet | Send a test mail from Gmail, or use **Setup & Test → Inject test mail** |

---

## Security notes

- `ADMIN_TOKEN` is a password you invent. It is the only thing protecting your
  mail API — keep it long, random, and private.
- Never put a token, password, or API key into code, a README, a screenshot, or a
  chat message. Secrets live in GitHub Secrets, in `wrangler secret`, or in the
  app's encrypted storage — nowhere else.
- To change the admin password later: update the `ADMIN_TOKEN` secret and re-run
  the Deploy workflow (or `npx wrangler secret put ADMIN_TOKEN`), then type the
  same new password into the app's **Keys** tab.
- This repo's `.gitignore` excludes `.dev.vars`, `.wrangler/`, `local.properties`,
  keystores (`*.jks`), and `google-services.json` so secrets can't be committed
  by accident.

---

## Project structure

```
shardmail/
├── android/            # Kotlin + Jetpack Compose app (Material 3 Expressive)
├── backend/            # Cloudflare Worker + D1 (TypeScript, Wrangler)
│   ├── src/            # email() + fetch() handlers, MIME parsing, OTP extract
│   └── migrations/     # D1 tables: messages, aliases, settings
├── .github/workflows/  # android.yml (build APK) + deploy-worker.yml (deploy backend)
├── docs/               # screenshots
├── LICENSE             # MIT
└── README.md           # this file
```

---

## Tech stack

- **App:** Kotlin, Jetpack Compose, Material 3 Expressive, Retrofit + OkHttp,
  Room (offline cache), EncryptedSharedPreferences (Android Keystore).
- **Backend:** Cloudflare Workers (TypeScript), D1 (SQLite), Wrangler.
- **Email:** Cloudflare Email Routing (catch-all → Worker).

---

## License

This project is licensed under the [MIT License](LICENSE). You are free to use,
copy, modify, and share it — including for commercial use — as long as the license
notice stays included.

## Contributing

Issues and pull requests are welcome. Please don't include any real domain, Worker
URL, token, or password in issues, screenshots, or code.
