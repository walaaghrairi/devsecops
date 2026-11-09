
pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "walaaeddine/devsecops"
    }

    triggers {
        githubPush()
    }

    stages {

        stage('Récupération du code source') {
            steps {
                checkout scm
                echo 'Code source récupéré avec succès.'
            }
        }

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

        stage('Affichage de la date système') {
            steps {
                sh 'date'
            }
        }

        stage('Clean') {
            steps {
                sh 'mvn -B clean'
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
                        mvn -B org.owasp:dependency-check-maven:13.0.0:check \
                            -DnvdApiKey="$NVD_API_KEY"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts(
                        artifacts: 'target/dependency-check-report.html',
                        allowEmptyArchive: true
                    )
                }
            }
        }

        stage('Compile') {
            steps {
                sh 'mvn -B -DskipTests=true compile'
            }
        }

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

        stage('Package') {
            steps {
                sh 'mvn -B -DskipTests=true package'
            }
        }

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

        stage('Acceptance - Ansible Infrastructure Verification') {
    steps {
        sh '''
            set -e

                    mkdir -p "$WORKSPACE/reports"

                    cd "$WORKSPACE/ansible"

                    ansible-playbook \
                        -i inventory.ini \
                        acceptance.yml \
                        2>&1 | tee "$WORKSPACE/reports/ansible-acceptance-report.txt"
                '''
            }

            post {
                always {
                    archiveArtifacts(
                        artifacts: 'reports/ansible-acceptance-report.txt',
                        allowEmptyArchive: true,
                        fingerprint: true
                    )
                }
            }
        }
    }
}
