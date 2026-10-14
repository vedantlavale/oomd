# Vehicle Service Management System (VSMS)

A web application for a vehicle service center: customers, vehicles, appointments, mechanics, job cards,
spare parts, invoices, payments and reports. College mini project.

**Stack:** Java 21 · Spring Boot 3.5 · Spring MVC · Spring Security · Spring Data JPA / Hibernate ·
Thymeleaf · Bootstrap 5 · MySQL 8 · Maven · JUnit 5

Everything runs as **one** Spring Boot project. Bootstrap and the icons are bundled (WebJars), so the
application needs no internet connection once it is built.

---

## 1. Install the tools (Windows)

You need **Java 21** and **MySQL 8**. Maven is *not* needed: the project contains the Maven wrapper
(`mvnw.cmd`) which downloads Maven by itself on first use.

Open **PowerShell** (not the old "cmd") and check what you already have:

```powershell
java -version      # must say 21.x
mysql --version    # must say 8.x
```

If something is missing:

* **Java 21** – install "Eclipse Temurin 21 (LTS)" from <https://adoptium.net> or run
  `winget install EclipseAdoptium.Temurin.21.JDK`, then open a **new** PowerShell window.
* **MySQL 8** – install "MySQL Community Server 8.x" from <https://dev.mysql.com/downloads/mysql/> with the
  installer's default options and remember the `root` password you choose.

If `mysql` is "not recognized", add its folder to the PATH for the current window (adjust the version folder if needed):

```powershell
$env:Path += ";C:\Program Files\MySQL\MySQL Server 8.0\bin"
mysql --version
```

> If `java -version` shows an older version, install Java 21 and make sure `JAVA_HOME` points to it:
> `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot"` (use your real folder name).

## 2. Create the database and a database user

The application must **not** connect as `root`. The helper script creates the database `vsms_db` and a dedicated
user `vsms_user` that can only access that database. It asks for the passwords on the screen and stores them nowhere.

From the project folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-database.ps1
```

You will be asked for (1) the MySQL `root` password and (2) a **new** password for `vsms_user` (8+ characters).
Remember the second one: the application asks for it when it starts.

<details>
<summary>Doing it by hand instead</summary>

```powershell
mysql -u root -p -e "source database/01-create-database.sql"
mysql -u root -p
```

then, inside the `mysql>` prompt (replace `<your-password>`):

```sql
CREATE USER 'vsms_user'@'localhost' IDENTIFIED BY '<your-password>';
GRANT ALL PRIVILEGES ON vsms_db.* TO 'vsms_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

(If the setup script cannot log in, these by-hand commands do the same thing. PowerShell does not support the `<` redirection that most MySQL tutorials use, which is why the commands above use `-e "source ..."`.)
</details>

The tables are created **automatically** the first time the application starts. If you would rather create them
yourself, run `mysql -u vsms_user -p vsms_db -e "source database/02-schema.sql"` and start the application with
`VSMS_DDL_AUTO=validate` (see the table in section 5).

## 3. Start the application

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run.ps1
```

The script asks for the database password (and, optionally, the administrator password), sets the environment
variables for that PowerShell window only, and runs `mvnw.cmd spring-boot:run`. The first start downloads the
dependencies (a few minutes). When you see `Started VsmsApplication`, open <http://localhost:8080>.

<details>
<summary>The same thing without the script</summary>

```powershell
$env:VSMS_DB_USERNAME = "vsms_user"
$env:VSMS_DB_PASSWORD = Read-Host "Database password"     # typed on screen, never saved in a file
$env:VSMS_ADMIN_PASSWORD = Read-Host "Choose an admin password (8+ chars, letters and digits)"
.\mvnw.cmd spring-boot:run
```

To build a single runnable file instead: `.\mvnw.cmd -DskipTests package`, then
`java -jar target\vsms-1.0.0.jar` (with the same environment variables set).
</details>

### First sign-in

| Account | Role | Password |
|---|---|---|
| `admin` | Administrator | the value of `VSMS_ADMIN_PASSWORD`; if you did not set one, a random password is **printed once** in the console on first start |
| `advisor` | Service advisor | same password as `admin` *(sample data only)* |
| `ravi.k`, `meena.j` | Mechanics | same password as `admin` *(sample data only)* |

* No password is stored in the source code. The initial administrator is created only when no administrator exists.
* **Forgot the administrator password?** Run `.\scripts\run.ps1 -ResetAdmin` (it asks for a new password), or set
  `VSMS_ADMIN_RESET=true` together with `VSMS_ADMIN_PASSWORD` for one start. The account is re-enabled and its password replaced.
* Sign in as `admin`, open **Users** to create real accounts for staff and mechanics, and **Change password** (top right menu).

### Sample data

On the first start with an empty database the application loads **fictional** demo data: 8 customers, 10 vehicles, 4 active
mechanics, 8 services, 12 spare parts (two low on stock), appointments for today and the next days, and 8 job cards
covering every status except cancelled, with invoices and payments (paid, part-paid, unpaid). Turn it off with
`.\scripts\run.ps1 -NoSampleData` (or `VSMS_LOAD_SAMPLE_DATA=false`). To start over, drop and recreate the database
(re-run step 2) and start the application again.

## 3b. macOS / Linux (optional)

The same project runs on macOS and Linux. The two things that usually go wrong are a default `java` older than 21 and a MySQL
that is not 8.x. Example with Homebrew:

```bash
brew install openjdk@21 mysql@8.4
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
brew services start mysql@8.4                      # then create the database and user as in step 2 (use the "by hand" commands)
VSMS_DB_PASSWORD='your-db-password' VSMS_ADMIN_PASSWORD='Your-admin-pass1' ./mvnw spring-boot:run
```

(`mvnw` fails with "release version 21 not supported" when `JAVA_HOME` still points to an older Java.)

## 4. What each role can do

| | Administrator | Service advisor | Mechanic |
|---|---|---|---|
| Dashboard | whole workshop | whole workshop | own jobs only |
| Customers, vehicles | add / edit / delete¹ | add / edit | – |
| Appointments | book / reschedule / cancel | book / reschedule / cancel | – |
| Service jobs | everything | everything | **only jobs assigned to them**: start, hold, resume, complete; notes, tasks, spare parts |
| Mechanics, service catalog | add / edit / activate / deactivate | view | – |
| Spare parts | add / edit / add stock / delete² | add / edit / add stock | view |
| Invoices, payments | generate, record, **reverse a payment** | generate, record | – |
| Reports | yes | yes | – |
| Users, change of roles | yes | – | – |

¹ only when nothing is linked to the record (customers with vehicles/appointments, vehicles with appointments cannot be deleted).
² only when the part was never used on a job.

Roles are enforced twice: by URL in `SecurityConfig` and with `@PreAuthorize` on each controller; the menus only show what a role may open.

## 5. Configuration (environment variables)

Nothing secret is in the source code; everything comes from environment variables (see `src/main/resources/application.yml`).

| Variable | Default | Meaning |
|---|---|---|
| `VSMS_DB_URL` | `jdbc:mysql://localhost:3306/vsms_db?...` | JDBC URL of the database |
| `VSMS_DB_USERNAME` | `vsms_user` | database user |
| `VSMS_DB_PASSWORD` | *(empty)* | database password |
| `VSMS_DDL_AUTO` | `update` | `update` creates/extends tables automatically; `validate` only checks them (use with `database/02-schema.sql`) |
| `VSMS_PORT` | `8080` | web port |
| `VSMS_ADMIN_USERNAME` | `admin` | username of the initial administrator |
| `VSMS_ADMIN_PASSWORD` | *(empty → random, printed once)* | password of the initial administrator (and of the sample accounts) |
| `VSMS_ADMIN_RESET` | `false` | `true` resets that administrator's password at start-up (recovery) |
| `VSMS_LOAD_SAMPLE_DATA` | `true` | load the fictional demo data on an empty database |
| `VSMS_DEFAULT_TAX_PERCENT` | `18.00` | tax rate suggested when generating an invoice (0 = no tax) |
| `VSMS_TEMPLATE_CACHE` | `true` | set to `false` while editing templates |

Opening hours, slot length, business name/address (shown on invoices) are under `vsms:` in `application.yml`.

## 6. Run the automated tests

```powershell
.\mvnw.cmd test
```

The tests use an in-memory H2 database in MySQL mode, so **MySQL is not needed** and your data is never touched.
They cover: the job-status state machine, invoice maths (BigDecimal, rounding), customers/vehicles (duplicate registration
numbers), appointment booking and slot conflicts, mechanic assignment and permissions, spare-part stock (never negative,
no double deduction, price kept at time of use), invoices (once per job, frozen amounts), payments (partial/full,
excess/negative rejected, reversal), concurrency (several threads racing for the same stock, invoice, balance and
time slot), database constraints, the role-by-URL access matrix, real login/logout, one full journey through the pages
(customer → vehicle → appointment → job → mechanic → invoice → payments → delivery), a crawler that follows every
link as each role (no broken links, no links a role cannot use, every form posts to a real route, every input has a
label), the initial-administrator/recovery logic and that `database/02-schema.sql` matches the entity mappings.

To run the same tests against a real MySQL (use an **empty scratch database** – the tests create and then drop all tables in it):

```powershell
mysql -u root -p -e "CREATE DATABASE vsms_db_test; GRANT ALL ON vsms_db_test.* TO 'vsms_user'@'localhost';"
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/vsms_db_test?useSSL=false&allowPublicKeyRetrieval=true"
$env:SPRING_DATASOURCE_USERNAME = "vsms_user"
$env:SPRING_DATASOURCE_PASSWORD = Read-Host "Database password"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver"
.\mvnw.cmd test
```

## 7. How the main rules work

* **Appointments** – 30-minute slots from 09:00 to 18:00, bookable up to 180 days ahead, never in the past. One active
  appointment per *service + date + time*, and one vehicle cannot be booked twice at the same time. Cancelling frees the slot.
  Booking locks the vehicle and service rows, so two people cannot take the same slot at the same moment.
* **Job statuses** – `CREATED → ASSIGNED → IN_PROGRESS ⇄ ON_HOLD → COMPLETED → INVOICED → DELIVERED`.
  Cancellation only from `CREATED`/`ASSIGNED`. `ASSIGNED` is set by assigning a mechanic, `INVOICED` by generating the invoice.
  A vehicle can be handed over (`DELIVERED`) only when its invoice is fully paid. Invalid moves are rejected by the service layer.
* **Spare parts** – stock is checked and reduced (once, under a row lock) when a part is added to a job, and put back when the
  line is removed or the job is cancelled. The unit price at that moment is copied onto the job line, so later price changes
  never change what a customer was charged.
* **Invoices** – generated by the server for a completed job: labour + parts (+ optional tax, rounded half-up), all
  `BigDecimal` / `DECIMAL(12,2)`. One invoice per job (also guaranteed by a unique database key). Amounts are stored and never recalculated.
* **Payments** – cash / UPI / card / other records (no payment gateway). Amount must be > 0, at most two decimals and not more than the
  outstanding balance; the invoice becomes *partially paid* / *paid* automatically. Only administrators can reverse a mistaken payment
  (it is kept for audit but no longer counts). Revenue = successful payments only.

## 8. Project layout

```
pom.xml, mvnw, mvnw.cmd          Maven build and wrapper
database/01-create-database.sql  creates the empty database
database/02-schema.sql           all tables (generated from the entities, verified)
scripts/setup-database.ps1       database + user setup (Windows)
scripts/run.ps1                  start the application (Windows)
src/main/java/com/vsms/
  entity/      JPA entities (UserAccount, Customer, Vehicle, Appointment, Mechanic, ServiceJob, ServiceTask,
               ServiceCatalogItem, SparePart, PartUsage, Invoice, Payment)
  repository/  Spring Data repositories      service/     business logic and transactions
  controller/  page handling                 dto/         form objects
  enums/       statuses, roles, methods      config/      security, properties, start-up (admin, sample data)
  security/    login user, actor lookup      exception/   business-rule and error handling
src/main/resources/templates/  Thymeleaf pages and reusable fragments (layout, components)
src/main/resources/static/     css/vsms.css, js/vsms.js
src/test/java/                 automated tests
```

## 9. Troubleshooting

| Message | Fix |
|---|---|
| `Access denied for user 'vsms_user'` | wrong database password; re-run `setup-database.ps1` to set a new one |
| `Unknown database 'vsms_db'` | step 2 was not done |
| `Communications link failure` | MySQL is not running (start the "MySQL80" service) or the port is not 3306 |
| `Port 8080 was already in use` | `.\scripts\run.ps1 -Port 8081` |
| `release version 21 not supported` / `invalid target release` | Java is older than 21: install Java 21 and check `java -version` |
| `running scripts is disabled on this system` | use the exact `powershell -ExecutionPolicy Bypass -File ...` commands shown above |
| Forgot the admin password | `.\scripts\run.ps1 -ResetAdmin` |

## 10. What has and has not been verified

Built and tested on macOS with Java 21 (Temurin/OpenJDK 21.0.12), Maven 3.9.11 (via the wrapper), **MySQL 8.4.11** and Chrome.

* Verified by running: the whole test suite on H2 **and** on MySQL 8.4; the application started against MySQL with the hand-applied
  `database/02-schema.sql` in `validate` mode; the first-run path with a generated administrator password; the SQL used by the setup
  script (including `MYSQL_PWD` with a password-protected user on the 8.4 client); the pages in real Chrome (desktop and 320/390/768 px
  widths: no console errors, no horizontal scrolling, working menus, dialogs, dependent drop-downs and booked-slot greying); the print
  output of the invoice, receipt and reports as A4 PDFs (one page each, without menus or forms); the packaged jar (`java -jar`).
* **Not executed here:** the PowerShell scripts (`scripts/*.ps1`) and `mvnw.cmd` – no Windows or PowerShell was available. They only
  wrap the commands shown in sections 2–3 and were written to be simple; if one fails, use the "by hand" commands, which are the same steps.
* Not covered: a real payment gateway (explicitly out of scope), e-mail/SMS, multi-branch operation.
