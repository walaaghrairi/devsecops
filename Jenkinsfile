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
            string(credentialsId: 'NVD_API_KEY',
                   variable: 'NVD_API_KEY')
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
            archiveArtifacts artifacts: 'target/dependency-check-report.html',
                             allowEmptyArchive: true
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
}        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $DOCKER_IMAGE:latest .'
            }
        }
        stage('Trivy Vulnerability Scan') {
    steps {
        sh '''
            mkdir -p reports

            curl -fsSL \
                https://raw.githubusercontent.com/aquasecurity/trivy/v0.76.1/contrib/html.tpl \
                -o reports/html.tpl

            trivy image \
                --timeout 15m \
                --format template \
                --template "@reports/html.tpl" \
                --output reports/trivy-report.html \
                amines2002/devsecops:latest

            trivy image \
                --timeout 15m \
                --format json \
                --output reports/trivy-report.json \
                amines2002/devsecops:latest
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

        archiveArtifacts artifacts: 'reports/trivy-report.json',
                         fingerprint: true
    }
}

        stage('Login DockerHub') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: '123456789',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                }
            }
        }
        /* stage('Push Docker Image') {
            steps {
                sh 'docker push $DOCKER_IMAGE:latest'
            }
        }*/
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
        sh '''#!/bin/bash
            set -o pipefail

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