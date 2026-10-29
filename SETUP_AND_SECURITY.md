# Interna — Setup, Security Architecture, Developer Login & Mobile-Money Payments

**App Name**: Interna  
**AI Language Partner**: Interna AI  
**Tagline**: *"One World. Every Language."*

---

## 1. Environment Variables & Secrets (Google AI Studio Secrets Panel)

Interna uses the **Secrets Gradle Plugin** reading from `.env` / `.env.example` (never hardcoding credentials in source code or `local.properties`).

Configure only the required keys in the **Secrets panel in Google AI Studio**:

| Secret Key | Purpose | Required? |
| :--- | :--- | :--- |
| `GEMINI_API_KEY` | Authenticates structured `gemini-2.5-flash` calls for AI Conversation, Contextual Translation, Pronunciation Analysis, and Dictionary AI usage notes when `INTERNA_BACKEND_URL` is set to `DIRECT_GEMINI_MODE`. | Required for AI calls in direct Gemini mode |
| `ADMIN_PROVISION_TOKEN` | Strong, **one-time** provisioning secret (minimum 10 characters; 32+ random characters recommended) used to provision `harinirinaianjara@gmail.com` as the initial administrator and sign the role grant. Permanently locked after initial provisioning so it cannot be reused. | Required once for initial Developer Console setup |
| `INTERNA_BACKEND_URL` | Optional HTTPS base URL (`https://...`) for an external production backend proxy. Defaults to `DIRECT_GEMINI_MODE` strictly for Gemini AI model requests; `DIRECT_GEMINI_MODE` is never used as a substitute for authorization, payment verification, or administrator privileges. | Optional |

---

## 2. Dedicated Developer Login & One-Time Provisioning (`harinirinaianjara@gmail.com`)

Interna enforces zero-trust administrator authentication:
1. **No Hardcoded Passwords**: No administrator password exists anywhere in source code, assets, Room seed scripts, or configuration files.
2. **Email Alone Grants Zero Privileges**: Registering or signing in with `harinirinaianjara@gmail.com` via normal user registration grants only a standard `USER` role.
3. **One-Time Secure Provisioning (No Prior Normal Registration Required)**:
   - Configure a strong `ADMIN_PROVISION_TOKEN` in the **Secrets panel in Google AI Studio**.
   - On the Sign-In screen, tap **Developer Login** and switch to **One-Time Initial Setup** (visible only while `adminProvisionTokenConsumed == false`).
   - Enter `harinirinaianjara@gmail.com`, your strong administrator password (hashed with 65,536-iteration PBKDF2-HMAC-SHA256 + 128-bit cryptographic salt), and your `ADMIN_PROVISION_TOKEN`:
     - **If `harinirinaianjara@gmail.com` does not exist yet**: Creates the verified administrator account directly without requiring prior normal-user registration.
     - **If `harinirinaianjara@gmail.com` already exists**: Verifies your existing password (or use Password Recovery first) and upgrades that exact account in-place to `ADMIN` **without creating a duplicate account**.
   - Atomically sets `adminProvisionTokenConsumed = true` in `application_settings`, stores an HMAC-SHA256 `grantSignature` (`userId:email:ADMIN`) in `roles_permissions`, and logs `ADMIN_ROLE_PROVISIONED` in `administrative_audit_logs`. Any subsequent attempt to reuse `ADMIN_PROVISION_TOKEN` is rejected and audited.
4. **Ongoing Developer Sign-In**:
   - Sign in anytime using `harinirinaianjara@gmail.com` and your configured password via **Developer Login** (or standard Sign-In).
   - Every operation in `AdminConsoleRepository` (users, subscriptions, mobile-money payment configs & approvals, surveys, support messages, and app settings) re-verifies `ACTIVE` status, `emailVerified`, authorized email, `ADMIN` role, and HMAC-SHA256 signature.

---

## 3. Mobile-Money Payment Methods (`Yas Money`, `Airtel Money`, `Orange Money`)

1. **Zero Unverified Numbers**: Default configurations for **Yas Money**, **Airtel Money**, and **Orange Money** ship with blank receiving account numbers, account names, prices, and instructions. Learners cannot submit transfers for a method until you configure verified receiving details.
2. **Developer Console Configuration**:
   - Sign in to **Developer Console → Payments & Approvals**.
   - For **Yas Money**, **Airtel Money**, and/or **Orange Money**, enter your verified receiving account number, receiving account name, currency (e.g., `MGA`), plan prices (`Monthly` 30 days, `Quarterly` 90 days, `Yearly` 365 days), and transfer instructions, then tap **Save Payment Configuration**.
3. **Manual Verification & Anti-Fraud Workflow**:
   - Learners select a Premium plan (`Monthly`, `Quarterly`, or `Yearly`) and preferred mobile-money provider, view your configured receiving details, transfer the funds, and submit their transaction reference, amount paid, payment date (`YYYY-MM-DD`), and optional proof of payment.
   - Every submission is saved with a unique `payreq_...` ID and marked strictly **`PENDING`** (`MANUAL_VERIFICATION`). Entering a transaction reference or attaching a screenshot **never** unlocks Premium automatically.
   - Duplicate/replayed transaction references (`normalizedTxRef`), underpayments below the configured plan price, future dates, and duplicate approvals (`@Transaction approveSubscriptionRequestAtomically` + unique `approvalRequestId`) are blocked.
   - Approving a request in **Developer Console → Payments & Approvals** activates all Premium features for the selected plan's duration (`30`, `90`, or `365` days), displays the active plan, duration, and expiration date to the learner, and records an immutable entry in `administrative_audit_logs`.

---

## 4. Database Schema & Non-Destructive Migration (`InternaDatabase` v2)

Interna persists 19 relational tables via Room (`InternaDatabase` version `2`) with `MIGRATION_1_2` preserving all existing v1 user accounts, roles, preferences, vocabulary, offline progress, conversations, surveys, support messages, and settings without data loss.
