# DevSecOps maturity phases — controls and evidence

One control per phase, all of them executing inside a single Jenkins pipeline
(`Jenkinsfile`, 20 stages: the five phase controls, their support stages, and a
notification stage). Evidence below is taken from **build #37**, which ran commit
`f464353` end to end and finished `SUCCESS`. The unit tests were added later and
were verified locally on the same sources (JDK 17, `mvn -B test`): 27 tests, 0
failures, 87% line coverage; the next Jenkins build shows the same stage output.

## The mapping

| # | Phase (diagram column) | Control | Jenkins stage | Proof |
|---|---|---|---|---|
| 1 | Pre-commit | Secret detection (Yelp detect-secrets via pre-commit) | `Pre-Commit Controls (Secret Scan)` | Console: `Detect secrets ... Passed` |
| 2 | Commit / CI | SAST (SpotBugs) + SCA (OWASP Dependency-Check) + container security (Trivy) + security unit tests (JUnit 5, coverage by JaCoCo) | `SAST - SpotBugs`, `SCA - OWASP Dependency-Check`, `Trivy Vulnerability Scan`, `Security Unit Tests - JUnit & JaCoCo` | 21 issues in SpotBugs trend, `dependency-check-report.html`, `Trivy Vulnerability Report` page, `Tests run: 27, Failures: 0` |
| 3 | Acceptance | Infrastructure/acceptance verification of a staging stack (Ansible) | `Acceptance - Staging Deployment & Verification` | `staging application answered after 24s`, `PLAY RECAP ok=5 failed=0` |
| 4 | Production | Secrets management check on the production credential source | `Secrets Management - Production Check` | `PASS: every required key is set and no tracked file contains its value.` |
| 5 | Operations | Continuous monitoring of the running application (blackbox-exporter probe) | `Operations - Continuous Monitoring (Blackbox)` | `probe_success 1`, `probe_http_status_code 200`, archived `monitoring-probe.txt` |

## Phase 1 — Pre-commit

**Control:** every commit is scanned for credentials before it can be accepted.
A `.pre-commit-config.yaml` runs the Yelp `detect-secrets` hook over every file;
the few harmless placeholders are allowlisted inline with
`# pragma: allowlist secret`. Jenkins runs the same hooks with `--all-files` in a
`python:3.12` container, so the gate does not depend on the developer having
installed the hook locally.

**Show:** stage `Pre-Commit Controls (Secret Scan)`.

```
Detect secrets...........................................................Passed
```

**Files:** `.pre-commit-config.yaml`.

**If asked "what does it catch?":** database passwords, API keys, tokens in any
tracked file. It is the reason the DB credentials moved to
`${SPRING_DATASOURCE_USERNAME}` / `${SPRING_DATASOURCE_PASSWORD}` and the
Kubernetes manifests use `valueFrom.secretKeyRef`.

## Phase 2 — Commit / CI

**Controls:** three, deliberately covering three different scopes.

- **SAST — SpotBugs** (`SAST - SpotBugs`): static analysis of the compiled Java.
  21 findings published to the Jenkins trend chart.
- **SCA — OWASP Dependency-Check** (`SCA - OWASP Dependency-Check`): CVEs in
  third-party libraries, report archived as `dependency-check-report.html`.
- **Container security — Trivy** (`Trivy Vulnerability Scan`): OS packages and
  jars inside the built image plus secret scanning, published as a browsable
  `Trivy Vulnerability Report` and archived as `reports/trivy-report.json`.
- **Security unit tests** (`Security Unit Tests - JUnit & JaCoCo`): 27 JUnit 5
tests over the services and the REST controllers, with the repositories mocked,
so they need neither a database nor a running Spring context. Unlike the three
analysers above these *do* gate: a failing test fails the build. JaCoCo writes
`target/site/jacoco/jacoco.xml`, and the SonarQube stage imports it, so the
dashboard shows real coverage instead of none.

```
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.16:report (report) @ student-management ---
[INFO] Analyzed bundle 'student-management' with 8 classes
```

**Show:** the `Security Unit Tests - JUnit & JaCoCo` stage (test count, JUnit
trend in the sidebar), then the coverage figure SonarQube reports.

**Show:** the `Trivy Vulnerability Report` link in the build sidebar (HTML, most
visual), then the SpotBugs trend graph across builds.

**If asked "does it block the build?":** no — all three are report-only today
(SpotBugs runs with `failOnError=false`, Dependency-Check has no CVSS failure
threshold set, and Trivy runs without `--exit-code`). Findings are visible and
trended, but a CVE does not stop delivery. That is a deliberate scope choice for
a green build, and it can be turned into a gate by failing on CRITICAL findings
that have a fix available.

## Phase 3 — Acceptance

**Control:** the artifact is deployed to an isolated acceptance stack and
verified before it is allowed near production.

- The stack is the same `docker-compose.yml` with every name and port
  overridden: project `pipeline-acceptance`, containers
  `acceptance-student-management` / `acceptance-mysql-service` /
  `acceptance-blackbox-exporter`, ports 8090 and 9116, its own volume.
- Jenkins waits for the application to answer HTTP, then runs the Ansible
  playbook `ansible/acceptance.yml`, which asserts the Docker service, the app
  container and the MySQL container are actually running.
- `post { always }` tears the stack down, so production is never touched.

**Show:** the stage, then the archived `ansible-acceptance-report.txt`.

```
   staging application answered after 24s
   PLAY RECAP
   localhost : ok=5 changed=0 unreachable=0 failed=0
   PASS: the staging stack passed every acceptance assertion.
```

**If asked "how is this different from production?":** different names, ports,
and volume, so both stacks can run at once; production is never the test subject.

## Phase 4 — Production

**Control:** before the production deploy, the pipeline proves that every
credential the stack needs is present in the real credential source
(`/opt/student-management/.env`, outside the repository) and that no value of
those credentials appears in any tracked file.

The check reads each value and greps the working tree with `git grep -F`, so a
password committed tomorrow fails the build.

**Show:** stage `Secrets Management - Production Check`.

```
   present: MYSQL_USER
   present: MYSQL_PASSWORD
   PASS: every required key is set and no tracked file contains its value.
```

**If asked "where do the secrets live?":** in an untracked file outside the repo,
injected into the containers by compose. `mysql-secret.example.yaml` documents
the Kubernetes shape without values.

## Phase 5 — Operations

**Control:** continuous monitoring of the deployed application.
`prom/blackbox-exporter` runs as a service in the stack; after the deploy Jenkins
asks it to probe `http://app:8089/student/students/getAllStudents` over the
compose network, polling up to 120 seconds for the app to boot.

Because the target is addressed by service name, the same monitor works for both
the production and the staging stack.

**Show:** stage `Operations - Continuous Monitoring (Blackbox)`, then the
archived `monitoring-probe.txt`.

```
   monitored target answered after 14s
      probe_duration_seconds 0.655884033
      probe_http_status_code 200
      probe_ip_protocol 4
      probe_success 1
   PASS: the deployed application answered a live monitoring probe.
```

**If asked "what does it actually verify?":** that the release is *serving*, not
merely *started* — DNS resolution, TCP connect and a real HTTP 200 from inside
the container network. Before this stage existed, a production container that
started and then crashed on startup would still have produced a green build.

**If asked "why not pentest / fault injection here?":** those are not
pipeline-able in this setup — they need an external attacker or a chaos target.
Monitoring is the control from the Operations column that genuinely runs in the
pipeline.

## Notification (not a phase control)

The last stage, `Notify - Build Result by Email`, mails the outcome: result,
job and build number, commit, and a table of the five phases with the stage that
covers each, plus links to the build and to the downloaded reports.

It is reporting, not a control — it does not gate anything. Recipient comes from
the `NOTIFY_TO` build parameter; when it is empty the stage says so and sends
nothing. A mail server problem is caught and logged, never a build failure.

**Requires** Manage Jenkins → System → *E-mail Notification* (SMTP host, port,
credentials) to be configured on the controller.

## Demo order (about five minutes)

1. Open the Jenkinsfile on GitHub and show it is an ordered pipeline, not a bag of
   tools — point at the phase comment banners.
2. Open the latest green build, stage view, and scroll right end to end: the order
   is pre-commit → CI checks → unit tests → acceptance → secrets → deploy →
   monitoring.
3. Pre-commit: the `Passed` line.
4. CI: `Trivy Vulnerability Report` (visual) and the SpotBugs trend.
5. Unit tests: `Tests run: 27, Failures: 0` and the coverage number SonarQube
   shows for the project.
6. Acceptance: the Ansible recap with the `acceptance-*` container names.
7. Production: the secrets `PASS` line.
8. Operations: the probe output with `probe_success 1`.
9. Close on the mapping table above.

## Limits worth stating before they are asked

- Trivy, Dependency-Check and SpotBugs are report-only; the build is green with
  known CVEs in Spring Boot, Tomcat and Swagger UI.
- If the NVD feed is unreachable, Dependency-Check falls back to its cached CVE
  database and logs a warning instead of failing the pipeline, so results can lag
  behind the newest disclosures until the feed recovers. A run that produces no
  report at all still fails the stage.
- The monitoring probe runs per build, not on a timer. The exporter is a
  long-running service, but Jenkins only queries it during a pipeline run
  (`cron` would make it periodic, at the cost of redeploying nightly).
- Coverage is measured, not enforced: there is no minimum threshold in JaCoCo and
  no `waitForQualityGate` step, so a drop in coverage does not fail the build.
- JaCoCo excludes the Lombok-generated entity code, so coverage is reported over
  the three services and three controllers; `StudentManagementApplication` (the
  `main` method) is counted and stays at 0%. That is why the reported percentage
  is over 8 classes and not over every line of the project.
- The tests are unit tests with mocked repositories; they do not exercise the
  MySQL schema, so they are not an integration test of the persistence layer.
- Email notification needs the `NOTIFY_TO` parameter set and an SMTP server
  configured in Jenkins; it reports on a build, it does not gate one.
