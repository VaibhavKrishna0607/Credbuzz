# CredBuzz 2.0 - Spring Boot + Next.js Learning Project

This is a **learning-focused boilerplate** for converting your MERN stack CredBuzz project to Spring Boot + Next.js. The code includes extensive comments explaining concepts and TODOs for you to complete.

## 🎯 Learning Approach

This project is structured to help you learn by:
1. **Reading the comments** - Every file has explanations comparing Express/React patterns to Spring Boot/Next.js
2. **Completing TODOs** - Features are partially implemented with TODOs for you to finish
3. **Understanding patterns** - See how MERN concepts translate to Java/Spring

---

## 📂 Project Structure

```
CredBuzz-2.0/
├── backend/                    # Spring Boot API
│   ├── pom.xml                 # Maven dependencies (like package.json)
│   └── src/main/java/com/credbuzz/
│       ├── CredBuzzApplication.java    # Main entry point
│       ├── entity/             # Data models (like Mongoose schemas)
│       │   ├── User.java
│       │   ├── Task.java
│       │   └── TaskStatus.java
│       ├── repository/         # Database access (like Mongoose queries)
│       │   ├── UserRepository.java
│       │   └── TaskRepository.java
│       ├── service/            # Business logic (controller logic in Express)
│       │   ├── UserService.java
│       │   └── TaskService.java
│       ├── controller/         # REST endpoints (routes in Express)
│       │   ├── AuthController.java
│       │   ├── TaskController.java
│       │   └── UserController.java
│       ├── dto/                # Request/Response objects
│       │   └── *.java
│       └── security/           # JWT authentication
│           ├── JwtService.java
│           ├── JwtAuthenticationFilter.java
│           └── SecurityConfig.java
│
└── frontend/                   # Next.js App
    ├── package.json
    ├── next.config.js
    └── src/
        ├── app/                # App Router pages
        │   ├── layout.tsx
        │   ├── page.tsx        # Home
        │   ├── login/page.tsx
        │   ├── register/page.tsx
        │   ├── tasks/page.tsx
        │   ├── tasks/[id]/page.tsx
        │   └── create-task/page.tsx
        ├── components/
        │   ├── Navbar.tsx
        │   └── ProtectedRoute.tsx
        └── context/
            └── AuthContext.tsx
```

---

## 🔄 Concept Mapping: MERN → Spring Boot + Next.js

### Backend Concepts

| MERN (Express + MongoDB) | Spring Boot + PostgreSQL |
|--------------------------|--------------------------|
| `server.js` | `CredBuzzApplication.java` |
| `router.post('/login', ...)` | `@PostMapping("/login")` |
| `mongoose.Schema` | `@Entity` class |
| `User.findOne({ email })` | `userRepository.findByEmail(email)` |
| `user.save()` | `userRepository.save(user)` |
| `req.body` | `@RequestBody` DTO class |
| `req.params.id` | `@PathVariable Long id` |
| `req.query.search` | `@RequestParam String search` |
| `req.user` (from middleware) | `SecurityContextHolder.getContext()` |
| `bcrypt.hash()` | `passwordEncoder.encode()` |
| `jwt.sign()` | `jwtService.generateToken()` |
| `jwt.verify()` | `jwtService.isTokenValid()` |
| `protect` middleware | `@PreAuthorize` or SecurityConfig |
| `.env` | `application.properties` |

### Frontend Concepts

| React + React Router | Next.js |
|---------------------|---------|
| `App.jsx` with Routes | `app/` folder structure |
| `<Route path="/tasks/:id">` | `app/tasks/[id]/page.tsx` |
| `useNavigate()` | `useRouter()` from `next/navigation` |
| `navigate('/home')` | `router.push('/home')` |
| `useParams()` | `useParams()` (same but from Next.js) |
| `<Link to="/">` | `<Link href="/">` |
| `import.meta.env.VITE_*` | `process.env.NEXT_PUBLIC_*` |
| Client-side only | Server + Client components |

---

## 🚀 Getting Started

### Prerequisites

- Java 17+ (Download from [Adoptium](https://adoptium.net/))
- Node.js 18+ 
- Maven (comes with most IDEs or install separately)

### Backend Setup

```bash
cd backend

# Run the application
./mvnw spring-boot:run
# Or on Windows:
mvnw.cmd spring-boot:run

# The API will start at http://localhost:8080
```

The H2 in-memory database is pre-configured for development. Access the H2 console at:
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:credbuzz_db`
- Username: `sa`
- Password: (leave empty)

### Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev

# The app will start at http://localhost:3000
```

---

## ✅ TODOs to Complete

The boilerplate has working core features. Here's what's left for you to implement:

### Backend TODOs

1. **Google OAuth Login** (`AuthController.java`)
   - Verify Google access token
   - Extract user info from Google API
   - Find or create user
   
2. **Password Reset with OTP** (`UserService.java`, `AuthController.java`)
   - Generate and store OTP
   - Send email with JavaMailSender
   - Verify OTP and reset password

3. **File Upload** (`UserController.java`, `TaskController.java`)
   - Handle avatar upload
   - Handle task submission files
   - Store files locally or in cloud storage

4. **Complete Task Filters** (`TaskService.java`)
   - Add category filter
   - Add skill filter
   - Add status filter

5. **Credit Locking System** (`TaskService.java`)
   - Lock credits when task is created
   - Return credits on cancellation
   - Transfer credits on completion

### Frontend TODOs

1. **My Tasks Page** (`/my-tasks`)
   - Show tasks created by user
   - Show tasks claimed by user
   
2. **Profile Page** (`/profile`)
   - Display user info
   - Edit profile
   - Upload avatar

3. **Google OAuth Button**
   - Integrate @react-oauth/google
   - Call backend /api/auth/google

4. **Mobile Menu**
   - Add responsive navigation

5. **Update Task Page**
   - Allow editing task details

---

## 📚 Learning Resources

### Spring Boot
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Baeldung Spring Tutorials](https://www.baeldung.com/spring-boot)
- [Spring Security Guide](https://www.baeldung.com/spring-security-tutorial)

### Next.js
- [Next.js Documentation](https://nextjs.org/docs)
- [Next.js App Router](https://nextjs.org/docs/app)
- [Next.js Learn Course](https://nextjs.org/learn)

### JWT Authentication
- [JWT.io](https://jwt.io/) - Understand JWT structure
- [Spring Boot JWT Tutorial](https://www.bezkoder.com/spring-boot-security-jwt/)

---

## 🏗️ Spring Boot Architecture Explained

```
Request → Controller → Service → Repository → Database
           ↓              ↓
         DTOs         Entities
```

1. **Controller**: Handles HTTP requests, validates input, returns responses
2. **Service**: Contains business logic, transactions
3. **Repository**: Database queries (auto-generated by Spring Data JPA)
4. **Entity**: Maps to database table (like Mongoose Schema)
5. **DTO**: Data Transfer Object - what you send/receive from API

---

## 🔐 Security Flow

```
1. User sends POST /api/auth/login with {email, password}
2. AuthController authenticates using AuthenticationManager
3. If valid, JwtService generates JWT token
4. Token returned to client
5. Client stores token, sends in Authorization header
6. JwtAuthenticationFilter intercepts requests
7. Filter validates token, sets SecurityContext
8. Controller can access user via SecurityContextHolder
```

---

## 💡 Tips

1. **Read the comments** - Every file has detailed explanations
2. **Use H2 Console** - See your data while developing
3. **Check application.properties** - All configuration is documented
4. **Compare with MERN code** - Side-by-side helps understanding
5. **Implement one TODO at a time** - Build incrementally

---

## 🤝 Need Help?

If you get stuck:
1. Check the comments in the code
2. Look at the corresponding Express code
3. Search Baeldung for Spring Boot examples
4. Check Next.js docs for React questions

Happy Learning! 🚀
