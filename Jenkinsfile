pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "walaaeddine/devsecops"
    }

    triggers {
        githubPush()
    }

    parameters {
        string(
            name: 'NOTIFY_TO',
            defaultValue: 'walikghrairi@gmail.com',
            description: 'Address that receives the build summary email. Leave empty to disable notification.'
        )
    }

    stages {

        stage('Récupération du code source') {
            steps {
                checkout scm
                echo 'Code source récupéré avec succès.'
            }
        }

        stage('Affichage de la date système') {
            steps {
                sh 'date'
            }
        }

        // ------------------------------------------------------------------
        // PHASE 1 - PRE-COMMIT
        // Hooks a developer runs before the code ever reaches the repository.
        // ------------------------------------------------------------------

        // Pre-commit control, enforced in CI: the same hooks developers install
        // locally via `pre-commit install` are re-run here against the whole tree.
        // A committed secret fails the build before anything is compiled or shipped.
        stage('Pre-Commit Controls (Secret Scan)') {
            steps {
                sh '''
                    if command -v pre-commit >/dev/null 2>&1; then
                        pre-commit run --all-files --show-diff-on-failure
                    else
                        # This agent has no python3-venv/ensurepip, so run the very
                        # same hooks in a container rather than installing packages
                        # on the host. The image tag also pins the scanner version.
                        docker run --rm \
                            -v "$WORKSPACE:/repo" \
                            -w /repo \
                            python:3.12 \
                            sh -c "command -v git >/dev/null || { apt-get update -qq && apt-get install -y -qq --no-install-recommends git >/dev/null; }; git config --global --add safe.directory /repo; pip install --quiet --no-cache-dir pre-commit; pre-commit run --all-files --show-diff-on-failure"
                    fi
                '''
            }
        }

        // ------------------------------------------------------------------
        // PHASE 2 - COMMIT / CI
        // Everything that inspects or builds the artifact. Nothing is published
        // or deployed from this phase.
        // ------------------------------------------------------------------

        stage('Clean') {
            steps {
                sh 'mvn -B clean'
            }
        }

        // Compile first: a build that cannot compile should fail in seconds, before
        // the slower analysers run.
        stage('Compile') {
            steps {
                sh 'mvn -B -DskipTests=true compile'
            }
        }

        stage('SCA - OWASP Dependency-Check') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'NVD_API_KEY',
                        variable: 'NVD_API_KEY'
                    )
                ]) {
                    sh '''
                        set +x
                        set -u

                        LOG="$WORKSPACE/reports/dependency-check.log"
                        REPORT="$WORKSPACE/target/dependency-check-report.html"
                        mkdir -p "$WORKSPACE/reports"

                        scan() {
                            mvn -B org.owasp:dependency-check-maven:13.0.0:check \
                                -DnvdApiKey="$NVD_API_KEY" \
                                -DfailOnError=false "$@" > "$LOG" 2>&1
                        }

                        # The stage reports, it does not gate: a CVE does not fail the
                        # build here. But the NVD feed being unreachable used to abort
                        # the whole pipeline even though the analysis still produced a
                        # report, so analysis errors are downgraded to warnings.
                        status=0
                        scan || status=$?

                        # No report at all means the control produced no evidence.
                        # Retry once against the cached CVE database before giving up.
                        if [ ! -s "$REPORT" ]; then
                            echo "   No report produced; retrying without the NVD update (cached data only)."
                            status=0
                            scan -DautoUpdate=false || status=$?
                        fi

                        cat "$LOG"

                        if grep -q "Unable to update 1 or more Cached Web DataSource" "$LOG" 2>/dev/null; then
                            echo "   WARNING: the NVD feed could not be reached; results are based on cached CVE data."
                        fi

                        if [ -s "$REPORT" ]; then
                            echo "   Report written: target/dependency-check-report.html"
                        else
                            echo "   FAIL: dependency-check produced no report (exit $status)."
                            exit 1
                        fi
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'target/dependency-check-report.html, reports/dependency-check.log',
                        allowEmptyArchive: true
                    )
                }
            }
        }

        // SAST needs the classes Compile just produced.
        stage('SAST - SpotBugs') {
            steps {
                sh 'mvn -B spotbugs:spotbugs'
            }
            post {
                always {
                    recordIssues(
                        tools: [
                            spotBugs(pattern: 'target/spotbugsXml.xml')
                        ]
                    )
                }
            }
        }

        // SonarQube analyses target/classes, so it belongs after Compile and before
        // the artifact is packaged.
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar \
                            -Dsonar.projectKey=devsecops \
                            -Dsonar.projectName=DevSecOps \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                    '''
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B -DskipTests=true package'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $DOCKER_IMAGE:latest .'
            }
        }

        stage('Trivy Vulnerability Scan') {
            steps {
                sh '''
                    # Keep the console readable: no progress bars, and reuse the
                    # vulnerability/Java DBs across builds instead of re-downloading.
                    export TRIVY_NO_PROGRESS=true
                    export TRIVY_CACHE_DIR="${TRIVY_CACHE_DIR:-$HOME/.cache/trivy}"

                    mkdir -p reports

                    curl -fsSL \
                        https://raw.githubusercontent.com/aquasecurity/trivy/v0.76.1/contrib/html.tpl \
                        -o reports/html.tpl

                    trivy image \
                        --timeout 15m \
                        --format template \
                        --template "@reports/html.tpl" \
                        --output reports/trivy-report.html \
                        "$DOCKER_IMAGE:latest"

                    trivy image \
                        --timeout 15m \
                        --format json \
                        --output reports/trivy-report.json \
                        "$DOCKER_IMAGE:latest"
                '''
            }
        }

        stage('Publish Trivy Report') {
            steps {
                publishHTML(target: [
                    reportName: 'Trivy Vulnerability Report',
                    reportDir: 'reports',
                    reportFiles: 'trivy-report.html',
                    keepAll: true,
                    alwaysLinkToLastBuild: true,
                    allowMissing: false
                ])

                archiveArtifacts(
                    artifacts: 'reports/trivy-report.json',
                    fingerprint: true
                )
            }
        }

        // ------------------------------------------------------------------
        // PHASE 3 - ACCEPTANCE
        // The build is accepted here, on a throwaway staging stack, BEFORE the
        // image is published or deployed. Nothing in this phase touches the
        // production containers: the stack runs under its own compose project,
        // container names, port and volume, courtesy of the ${...:-default}
        // values in docker-compose.yml.
        // ------------------------------------------------------------------
        stage('Acceptance - Staging Deployment & Verification') {
            steps {
                sh '''
                    set -eu

                    ENV_FILE=/opt/student-management/.env
                    COMPOSE_FILE="$WORKSPACE/docker-compose.yml"
                    PLAYBOOK_DIR="$WORKSPACE/ansible"
                    REPORT="$WORKSPACE/reports/ansible-acceptance-report.txt"

                    PROJECT=pipeline-acceptance
                    export APP_CONTAINER_NAME=acceptance-student-management
                    export MYSQL_CONTAINER_NAME=acceptance-mysql-service
                    export BLACKBOX_CONTAINER_NAME=acceptance-blackbox-exporter
                    export APP_PORT=8090
                    # Host ports are global, so the staging monitor cannot reuse
                    # the one production already publishes.
                    export BLACKBOX_PORT=9116

                    mkdir -p "$WORKSPACE/reports"

                    compose() {
                        docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
                    }

                    echo "== Acceptance: staging stack =="
                    echo "   project:  $PROJECT"
                    echo "   app:      $APP_CONTAINER_NAME on port $APP_PORT"
                    echo "   database: $MYSQL_CONTAINER_NAME (separate volume; production untouched)"

                    compose up -d --no-build

                    # A running container is not a working application: wait until the
                    # staging app actually answers a request before asserting anything.
                    # The app serves under the /student context path set in
                    # application.properties, so the probe has to include it.
                    APP_URL="http://127.0.0.1:$APP_PORT/student/students/getAllStudents"
                    echo "   waiting for $APP_URL (up to 240s; the first run also initialises its database)"
                    waited=0
                    while [ "$waited" -lt 240 ]; do
                        if curl -fsS -o /dev/null "$APP_URL" 2>/dev/null; then
                            break
                        fi
                        sleep 2
                        waited=$((waited + 2))
                    done

                    if [ "$waited" -ge 240 ]; then
                        echo "   FAIL: the staging application did not answer within 240s."
                        compose ps || true
                        compose logs --tail=80 || true
                        exit 1
                    fi

                    echo "   staging application answered after ${waited}s"

                    EXTRA_ARGS="-e app_container=$APP_CONTAINER_NAME -e mysql_container=$MYSQL_CONTAINER_NAME"

                    # Prefer a native ansible-playbook. Until it is installed on the
                    # agent, run the same playbook in a container and hand it the host's
                    # Docker CLI and socket, so `docker info` and `docker inspect` still
                    # see the containers docker compose just created.
                    if command -v ansible-playbook >/dev/null 2>&1; then
                        echo "   ansible: $(ansible-playbook --version | head -n 1)"
                        if ( cd "$PLAYBOOK_DIR" && ansible-playbook -i inventory.ini acceptance.yml $EXTRA_ARGS ) > "$REPORT" 2>&1; then
                            status=0
                        else
                            status=$?
                        fi
                    else
                        DOCKER_BIN=$(command -v docker || true)
                        DOCKER_BIN=$(readlink -f "${DOCKER_BIN:-/nonexistent}" 2>/dev/null || true)
                        if [ -z "$DOCKER_BIN" ] || [ ! -x "$DOCKER_BIN" ]; then
                            msg="FAIL: ansible-playbook is not installed and no docker CLI is available to fall back to."
                            echo "$msg" > "$REPORT"
                            echo "$msg"
                            exit 1
                        fi
                        echo "   ansible-playbook not on the agent; running the playbook in python:3.12"
                        if docker run --rm \
                                -v "$PLAYBOOK_DIR:/work" \
                                -w /work \
                                -e HOME=/tmp \
                                -e EXTRA_ARGS="$EXTRA_ARGS" \
                                -v /var/run/docker.sock:/var/run/docker.sock \
                                -v "$DOCKER_BIN:/usr/bin/docker:ro" \
                                python:3.12 \
                                sh -c 'pip install --quiet --no-cache-dir "ansible-core>=2.16,<2.19" && ansible-playbook -i inventory.ini acceptance.yml $EXTRA_ARGS' \
                                > "$REPORT" 2>&1; then
                            status=0
                        else
                            status=$?
                        fi
                    fi

                    echo "---- acceptance report ----"
                    cat "$REPORT"
                    echo "---------------------------"

                    if [ "$status" -ne 0 ]; then
                        echo "FAIL: acceptance verification failed (exit $status). See the report above."
                        exit "$status"
                    fi

                    echo "   PASS: the staging stack passed every acceptance assertion."
                '''
            }

            post {
                always {
                    sh '''
                        # Tear the staging stack down either way. The named volume is
                        # kept so later builds start against an already-initialised
                        # database instead of re-running MySQL's first-time setup.
                        docker compose -p pipeline-acceptance \
                            --env-file /opt/student-management/.env \
                            -f "$WORKSPACE/docker-compose.yml" \
                            down || true
                    '''
                    archiveArtifacts(
                        artifacts: 'reports/ansible-acceptance-report.txt',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }

        // ------------------------------------------------------------------
        // PHASE 4 - PRODUCTION
        // Publication and deployment. Everything that can reject the release has
        // already run.
        // ------------------------------------------------------------------

        stage('Login DockerHub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: '123456789',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'  // pragma: allowlist secret (credential variable name, not a secret)
                    )
                ]) {
                    sh '''
                        set +x
                        echo "$DOCKER_PASS" | docker login \
                            -u "$DOCKER_USER" --password-stdin
                    '''
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                sh 'docker push $DOCKER_IMAGE:latest'
            }
        }

        stage('Secrets Management - Production Check') {
            steps {
                sh '''
                    set -eu

                    ENV_FILE=/opt/student-management/.env
                    REQUIRED_KEYS="MYSQL_USER MYSQL_PASSWORD"

                    echo "== Secrets management check =="
                    echo "   credential source: $ENV_FILE"

                    if [ ! -r "$ENV_FILE" ]; then
                        echo "   FAIL: $ENV_FILE is missing or unreadable, so production credentials have no source."
                        exit 1
                    fi

                    missing=""
                    for key in $REQUIRED_KEYS; do
                        if grep -qE "^${key}=" "$ENV_FILE"; then
                            echo "   present: $key"
                        else
                            echo "   MISSING: $key"
                            missing="$missing $key"
                        fi
                    done

                    if [ -n "$missing" ]; then
                        echo "   FAIL: required keys are absent from $ENV_FILE:$missing"
                        exit 1
                    fi

                    # Compare the live values against the tracked tree. Only file
                    # names are reported, so the check never prints a secret.
                    leaks=0
                    for key in $REQUIRED_KEYS; do
                        value=$(grep -E "^${key}=" "$ENV_FILE" | head -n 1 | cut -d= -f2- | tr -d '"' | tr -d "'")
                        if [ "${#value}" -lt 4 ]; then
                            echo "   skipped value check for $key (empty or too short to be meaningful)"
                            continue
                        fi
                        hits=$(git grep -l -F -e "$value" -- . 2>/dev/null || true)
                        if [ -n "$hits" ]; then
                            leaks=$((leaks + 1))
                            echo "   LEAK: the value of $key is hardcoded in these tracked files:"
                            echo "$hits" | sed 's/^/      /'
                        fi
                    done

                    if [ "$leaks" -ne 0 ]; then
                        echo "   FAIL: credentials must live only in the credential source, never in tracked files."
                        exit 1
                    fi

                    echo "   PASS: every required key is set and no tracked file contains its value."
                '''
            }
        }

        stage('Deploy Application with Docker Compose') {
            steps {
                sh '''
                    set -e

                    echo "Validating Docker Compose configuration..."

                    docker compose \
                        --env-file /opt/student-management/.env \
                        -f "$WORKSPACE/docker-compose.yml" \
                        config -q

                    echo "Pulling latest Docker image..."

                    docker compose \
                        --env-file /opt/student-management/.env \
                        -f "$WORKSPACE/docker-compose.yml" \
                        pull

                    echo "Starting Student Management application..."

                    docker compose \
                        --env-file /opt/student-management/.env \
                        -f "$WORKSPACE/docker-compose.yml" \
                        up -d

                    echo "Checking running containers..."

                    docker compose \
                        --env-file /opt/student-management/.env \
                        -f "$WORKSPACE/docker-compose.yml" \
                        ps

                    echo "Recent application logs..."

                    docker compose \
                        --env-file /opt/student-management/.env \
                        -f "$WORKSPACE/docker-compose.yml" \
                        logs --tail=50
                '''
            }
        }

        // ------------------------------------------------------------------
        // PHASE 5 - OPERATIONS
        // The release is live, so the question changes from "is it correct?" to
        // "is it still serving?". blackbox-exporter probes the running
        // application from inside the compose network and reports the outcome
        // phase by phase (DNS, connect, first byte, status code).
        // ------------------------------------------------------------------
        stage('Operations - Continuous Monitoring (Blackbox)') {
            steps {
                sh '''
                    set -eu

                    ENV_FILE=/opt/student-management/.env
                    COMPOSE_FILE="$WORKSPACE/docker-compose.yml"
                    SNAPSHOT="$WORKSPACE/reports/monitoring-probe.txt"
                    MONITOR="${BLACKBOX_PROBE_URL:-http://127.0.0.1:9115}"

                    # Addressed by service name on the compose network, so the same
                    # target is correct for the production stack and for staging.
                    TARGET="http://app:8089/student/students/getAllStudents"

                    mkdir -p "$WORKSPACE/reports"

                    echo "== Operations: continuous monitoring =="
                    echo "   monitor: $MONITOR (blackbox-exporter)"
                    echo "   target:  $TARGET"

                    # The monitor is part of the stack; start it in case the deploy
                    # stage only brought up the application.
                    docker compose \
                        --env-file "$ENV_FILE" \
                        -f "$COMPOSE_FILE" \
                        up -d blackbox

                    probe() {
                        curl -fsS -G "$MONITOR/probe" \
                            --data-urlencode "target=$TARGET" \
                            --data-urlencode "module=http_2xx" 2>/dev/null
                    }

                    waited=0
                    while [ "$waited" -lt 120 ]; do
                        if probe | tee "$SNAPSHOT" | grep -q '^probe_success 1$'; then
                            break
                        fi
                        sleep 2
                        waited=$((waited + 2))
                    done

                    if ! grep -q '^probe_success 1$' "$SNAPSHOT" 2>/dev/null; then
                        echo "   FAIL: the monitor could not confirm the application is serving (120s)."
                        echo "   last probe output:"
                        sed 's/^/      /' "$SNAPSHOT" 2>/dev/null || true
                        docker compose \
                            --env-file "$ENV_FILE" \
                            -f "$COMPOSE_FILE" \
                            logs --tail=20 blackbox || true
                        exit 1
                    fi

                    echo "   monitored target answered after ${waited}s"
                    grep -E '^probe_(success|http_status_code|duration_seconds|ip_protocol) ' "$SNAPSHOT" \
                        | sed 's/^/      /' || true

                    echo "   PASS: the deployed application answered a live monitoring probe."
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reports/monitoring-probe.txt',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }

        // ------------------------------------------------------------------
        // Notification
        // Last, so the mail describes a finished run: result, commit, and the
        // control that covered each phase, with a link back to the build.
        // A mail server problem is reported but never fails the build.
        // ------------------------------------------------------------------
        stage('Notify - Build Result by Email') {
            steps {
                script {
                    def recipient = params.NOTIFY_TO?.trim()

                    if (!recipient) {
                        echo 'No recipient configured: set the NOTIFY_TO parameter to send the build summary.'
                        return
                    }

                    def result = currentBuild.currentResult
                    def shortCommit = env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : 'unknown'

                    def rows = [
                        ['Pre-commit', 'Secret detection (detect-secrets)', 'Pre-Commit Controls (Secret Scan)'],
                        ['Commit / CI', 'SAST, SCA and container scan', 'SAST - SpotBugs, SCA - OWASP Dependency-Check, Trivy Vulnerability Scan'],
                        ['Acceptance', 'Staging stack verified by Ansible', 'Acceptance - Staging Deployment & Verification'],
                        ['Production', 'Secrets management check', 'Secrets Management - Production Check'],
                        ['Operations', 'Continuous monitoring (blackbox probe)', 'Operations - Continuous Monitoring (Blackbox)']
                    ]

                    // Index the row explicitly: destructuring a List across several
                    // closure parameters does not happen here, so the whole row was
                    // printed in the first column and the other two came out null.
                    def escape = { value ->
                        (value ?: '').toString()
                            .replace('&', '&amp;')
                            .replace('<', '&lt;')
                            .replace('>', '&gt;')
                    }

                    def tableRows = rows.collect { row ->
                        "<tr><td><b>${escape(row[0])}</b></td><td>${escape(row[1])}</td>" +
                            "<td><code>${escape(row[2])}</code></td></tr>"
                    }.join('')

                    def body = """
                        <html><body style="font-family:Segoe UI,Arial,sans-serif;color:#1f2328">
                        <h2 style="margin-bottom:4px">DevSecOps pipeline &mdash; ${result}</h2>
                        <p style="margin-top:0;color:#59636e">
                          ${env.JOB_NAME} build <b>#${env.BUILD_NUMBER}</b> &middot; commit <code>${shortCommit}</code>
                        </p>
                        <table cellpadding="6" cellspacing="0" style="border-collapse:collapse;border:1px solid #d1d9e0;font-size:13px">
                        <tr style="background:#f6f8fa">
                          <th align="left">Phase</th><th align="left">Control</th><th align="left">Pipeline stage</th>
                        </tr>
                        ${tableRows}
                        </table>
                        <p style="font-size:13px">
                        <a href="${env.BUILD_URL}">Open the build</a> &middot;
                        <a href="${env.BUILD_URL}artifact/reports/">Download the reports</a>
                        </p>
                        <p style="font-size:12px;color:#59636e">
                        Sent by Jenkins after a pipeline run. Change the recipient with the NOTIFY_TO build parameter.
                        </p>
                        </body></html>
                        """

                    try {
                        mail(
                            to: recipient,
                            subject: "[${result}] ${env.JOB_NAME} #${env.BUILD_NUMBER} - ${shortCommit}",
                            body: body,
                            mimeType: 'text/html'
                        )
                        echo "Build summary emailed to ${recipient}."
                    } catch (Exception exc) {
                        echo "Email notification failed: ${exc.message}"
                        echo 'Check Manage Jenkins > System > E-mail Notification (SMTP host, port, credentials).'
                    }
                }
            }
        }
    }
}
