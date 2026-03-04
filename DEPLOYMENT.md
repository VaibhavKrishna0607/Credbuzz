# CredBuzz 2.0 - Setup & Deployment Guide

---

## 🗄️ Part 1: Local Database Setup (PostgreSQL)

### Step 1: Install PostgreSQL

1. Download from: https://www.postgresql.org/download/windows/
2. Run the installer, set a password for the `postgres` user (remember it!)
3. Keep the default port `5432`
4. Finish the installation

### Step 2: Create the Database

Open **pgAdmin** (installed with PostgreSQL) or use the **SQL Shell (psql)**:

```sql
-- In psql or pgAdmin query tool:
CREATE DATABASE credbuzz_db;
```

That's it! Spring Boot auto-creates all the tables on first startup.

### Step 3: Configure the Backend

The backend reads credentials from environment variables. The defaults are:
- Username: `postgres`
- Password: `postgres`

If your PostgreSQL password is different, set it before running:

**Option A: Set in terminal (temporary)**
```powershell
$env:DB_PASSWORD = "your_actual_password"
cd backend
mvn spring-boot:run
```

**Option B: Create backend/.env file (permanent)**

Not natively supported by Spring Boot, but the `application-dev.properties` defaults work if your PostgreSQL credentials match `postgres/postgres`.

If your password is different, edit `backend/src/main/resources/application-dev.properties`:
```properties
spring.datasource.password=your_actual_password
```

### Step 4: Run the App Locally

**Terminal 1 - Backend:**
```powershell
cd backend
mvn spring-boot:run
```

**Terminal 2 - Frontend:**
```powershell
cd frontend
npm run dev
```

Open http://localhost:3000 in your browser.

### Seed Users (auto-created on first run)

| Email | Password | Credits |
|---|---|---|
| alice@example.com | password123 | 100 |
| bob@example.com | password123 | 50 |
| charlie@example.com | password123 | 75 |

Or just register new users through the app!

---

## 🚀 Part 2: Deploy to Render + Vercel

### Architecture

```
[Vercel - Frontend]  →  [Render - Backend API]  →  [Render - PostgreSQL DB]
     Next.js                Spring Boot                  PostgreSQL
```

---

### Step 1: Push to GitHub

Make sure your code is in a GitHub repo:

```powershell
cd "V:\My Projects\CredBuzz-2.0"
git init
git add .
git commit -m "CredBuzz 2.0 - ready for deployment"
git remote add origin https://github.com/YOUR_USERNAME/CredBuzz-2.0.git
git push -u origin main
```

---

### Step 2: Create PostgreSQL on Render

1. Go to https://render.com and sign in with GitHub
2. Click **"New"** → **"PostgreSQL"**
3. Fill in:
   - **Name:** `credbuzz-db`
   - **Region:** Pick the closest to you
   - **Plan:** **Free** (90-day limit) or **Starter** ($7/mo)
4. Click **"Create Database"**
5. Once created, go to the database page and copy the **Internal Database URL** — it looks like:
   `postgres://user:pass@host/credbuzz_db`
6. **Convert it to JDBC format** by replacing `postgres://` with `jdbc:postgresql://`:
   `jdbc:postgresql://credbuzz_db_user:7Lpvozu1kemKWEEHviqGZdA2atscnZZ9@dpg-d6k4707gi27c73cmv700-a/credbuzz_db`
   Save this — you'll need it in Step 3.

---

### Step 3: Deploy Backend on Render

1. Click **"New"** → **"Web Service"**
2. Connect your GitHub repo (`CredBuzz-2.0`)
3. Configure:
   - **Name:** `credbuzz-backend`
   - **Region:** Same as your database
   - **Root Directory:** `backend`
   - **Runtime:** **Docker** (it will use your Dockerfile)
4. Click **"Create Web Service"**

#### Set Environment Variables

Go to your web service → **Environment** tab → Add these:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://user:pass@host/credbuzz_db` (the JDBC URL from Step 2) |
| `JWT_SECRET` | (generate: `openssl rand -hex 32` in terminal) |
| `CORS_ORIGINS` | `https://your-app.vercel.app` (set after Vercel deploy) |
| `PORT` | `8080` |

#### Get Your Backend URL

After deployment, Render gives you a URL like:
`https://credbuzz-backend.onrender.com`

> **Note:** Free tier services spin down after 15 min of inactivity. First request after sleep takes ~30s. Upgrade to Starter ($7/mo) to avoid this.

---

### Step 4: Deploy Frontend on Vercel

1. Go to https://vercel.com and sign in with GitHub
2. Click **"Add New Project"** → Import your `CredBuzz-2.0` repo
3. Set **Root Directory** to `frontend`
4. Framework Preset: **Next.js** (auto-detected)
5. Add **Environment Variable**:

| Variable | Value |
|---|---|
| `NEXT_PUBLIC_API_URL` | `https://credbuzz-backend.onrender.com` (your Render URL from Step 3) |

6. Click **Deploy**

#### Get Your Frontend URL

Vercel gives you a URL like: `https://credbuzz.vercel.app`

---

### Step 5: Update CORS

Go back to Render → Backend service → **Environment** → Update:

| Variable | Value |
|---|---|
| `CORS_ORIGINS` | `https://credbuzz.vercel.app` (your Vercel URL) |

Render will auto-redeploy.

---

### Step 6: Test It!

1. Open your Vercel URL
2. Register a new account
3. Create a task, place bids, test the full flow

---

## 🔧 Troubleshooting

### "Connection refused" on localhost
- Make sure PostgreSQL is running: `pg_isready` in terminal
- Check password: try connecting with `psql -U postgres -d credbuzz_db`

### "Table not found" errors
- Spring Boot creates tables automatically with `ddl-auto=update`
- If schema changed, you can drop and recreate: `DROP DATABASE credbuzz_db; CREATE DATABASE credbuzz_db;`

### Render build fails
- Check logs in Render dashboard
- Make sure root directory is set to `backend`
- Verify Dockerfile exists in `backend/Dockerfile`

### Frontend can't reach backend (CORS errors)
- Check `CORS_ORIGINS` env var on Render matches your Vercel URL exactly
- Include `https://` but NO trailing slash

### Render PostgreSQL connection issues
- Make sure `DATABASE_URL` starts with `jdbc:postgresql://` (not `postgres://`)
- Copy the **Internal Database URL** from Render and convert the prefix

### Backend slow to respond (free tier)
- Render free tier spins down after 15 min of inactivity
- First request after sleep takes ~30 seconds to cold-start
- This is normal — upgrade to Starter plan ($7/mo) to keep it always on

---

## 📁 File Structure Summary

```
backend/
├── Dockerfile                          # Docker build for Render
├── pom.xml                             # Maven deps (PostgreSQL + H2)
├── .env.example                        # Template for local env vars
├── system.properties                   # Java version for deployment
└── src/main/resources/
    ├── application.properties          # Shared config (reads env vars)
    ├── application-local.properties    # H2 in-memory (zero setup, default)
    ├── application-dev.properties      # Local PostgreSQL config
    ├── application-prod.properties     # Render PostgreSQL config
    └── data.sql                        # Seed users (password: password123)

frontend/
├── .env.example                        # Template for env vars
├── .env.local                          # Local dev config (gitignored)
└── next.config.js                      # Reads NEXT_PUBLIC_API_URL
```
