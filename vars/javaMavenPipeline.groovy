def call(Map config) {
    pipeline {
        agent any

        stages {
            stage('Checkout Code') {
                steps {
                    echo "Cloning the repository from ${config.repoUrl}..."
                    git branch: config.branch, url: config.repoUrl
                }
            }

            stage('Clean') {
                steps {
                    echo "Running mvn clean..."
                    sh 'mvn clean'
                }
            }

            stage('Compile') {
                steps {
                    echo "Compiling source code..."
                    sh 'mvn compile'
                }
            }
stage('Build') { // ✅ New Build Stage Added
                steps {
                    echo "Building the project..."
                    sh 'mvn package -DskipTests'
                }
            }
            stage('Run test') {
                steps {
                    echo "Running tests..."
                    sh 'mvn test'
                }
            }
            stage('SonarQube Analysis') {
                steps {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        script {
                            echo "Running SonarQube analysis..."
                            sh """
                                mvn sonar:sonar \
                                -Dsonar.projectKey=${config.sonarProjectKey} \
                                -Dsonar.host.url=http://34.207.131.166:9000 \
                                -Dsonar.login=$SONAR_TOKEN
                            """
                        }
                    }
                }
            }

            stage('Check SonarQube Quality Gate') {
                steps {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        script {
                            echo "Waiting for SonarQube analysis to complete..."
                            sleep(time: 30, unit: 'SECONDS')

                            def sonarCheck = sh(
                                script: """
                                    curl -s -u $SONAR_TOKEN: \
                                    'http://34.207.131.166:9000/api/qualitygates/project_status?projectKey=${config.sonarProjectKey}' | jq -r .projectStatus.status
                                """,
                                returnStdout: true
                            ).trim()

                            echo "SonarQube Quality Gate Status: ${sonarCheck}"

                            if (sonarCheck == 'ERROR') {
                                error "Pipeline failed: Quality Gate did not pass!"
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                echo "Pipeline execution completed!"
            }
            failure {
                echo "Pipeline failed!"
            }
        }
    }
}
