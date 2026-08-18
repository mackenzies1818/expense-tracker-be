# expense-tracker-be


how to setup spring boot project from CLI:

`brew install --cask temurin`

check:

`java --version`

`
➜  expense-tracker-be git:(main) ✗ java --version
openjdk 26.0.2 2026-07-21
OpenJDK Runtime Environment Temurin-26.0.2+10 (build 26.0.2+10)
OpenJDK 64-Bit Server VM Temurin-26.0.2+10 (build 26.0.2+10, mixed mode, sharing)
`

install maven:

`brew install maven`

check:

`mvn --version`


`
➜  expense-tracker-be git:(main) ✗ mvn --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: /opt/homebrew/Cellar/maven/3.9.16/libexec
Java version: 26.0.2, vendor: Homebrew, runtime: /opt/homebrew/Cellar/openjdk/26.0.2/libexec/openjdk.jdk/Contents/Home
Default locale: en_CA, platform encoding: UTF-8
OS name: "mac os x", version: "14.6", arch: "aarch64", family: "mac"
`



https://start.spring.io/ - download cosas and import maven dependencies 

build mvn project:
`
./mvnw spring-boot:run
`


DB
flyway migrate

- migration run as a part of :./mvnw spring-boot:run

**AUTH FLOW:**

POST /auth/login

            email + password
            │
            ▼
            Find user in PostgreSQL
            │
            ▼
            PasswordEncoder.matches()
            │
            ▼
            Password correct?
            │
            ▼
            Create JWT
            │
            ├── sub = user ID/email
            ├── iat = now
            └── exp = expiration
            │
            ▼
            Sign with JWT_SECRET
            │
            ▼
            Return JWT

**API FLOW:**

        React
        │
        │ Authorization: Bearer JWT
        ▼
        Spring Security Filter Chain
        │
        ▼
        Extract JWT
        │
        ▼
        Verify signature with JWT_SECRET
        │
        ▼
        Check expiration
        │
        ▼
        Extract subject
        │
        ▼
        Create Authentication
        │
        ▼
        SecurityContext
        │
        ▼
        Controller
        │
        │ Authentication authentication
        ▼
        Service
        │
        ▼
        PostgreSQL

**Why BCrypt specifically?**

BCrypt is deliberately designed to be relatively expensive to compute.

That's useful because an attacker who gets your database can't cheaply try billions of passwords per second.

For a modern Spring application, BCrypt is still a reasonable choice, although password hashing options such as Argon2 are also worth knowing about.


**How does Authentication Object get created in the Controllers:**

                HTTP Request
                ↓
                Spring Security Filter Chain
                ↓
                JWT Authentication Filter - reads header and extracts JWT
                ↓
                Controller
There is a securitycontext that spring creates the authentication object after it verifies the JWT token, spring extracts the user and creates the object