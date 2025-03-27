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

            stage('Run Tests') {
                steps {
                    echo "Running tests..."
                    sh 'mvn test'
                }
            }

            stage('SonarQube Analysis') {
                steps {
        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
            sh """
                mvn sonar:sonar \
                -Dsonar.projectKey=${config.sonarProjectKey} \
                -Dsonar.host.url=http://34.207.131.166:9000 \
                -Dsonar.login=$SONAR_TOKEN
            """
        }
            }
            }

            stage('Check SonarQube Quality Gate') {
                steps {
                    script {
                        echo "Waiting for SonarQube analysis to complete..."
                        sleep(time: 30, unit: 'SECONDS') // Wait for Sonar analysis

                        def criticalIssues = org.mycompany.utils.SonarQubeHelper.getCriticalIssues(
                            config.sonarUrl, config.sonarToken, config.sonarProjectKey
                        )

                        echo "Critical vulnerabilities found: ${criticalIssues}"

                        if (criticalIssues > 5) {
                            error "Pipeline failed: More than 5 critical vulnerabilities found!"
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
