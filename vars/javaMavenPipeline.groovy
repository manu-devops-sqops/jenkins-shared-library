def call(Map config) {
    pipeline {
        agent any

        environment {
            DOCKER_IMAGE = "your-dockerhub-username/${config.imageName ?: 'default-image'}"
            IMAGE_TAG = "latest"
        }

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

            stage('Build') {
                steps {
                    echo "Building the project..."
                    sh 'mvn package -DskipTests'
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
                        script {
                            echo "Running SonarQube analysis..."
                            sh """
                                mvn sonar:sonar \
                                -Dsonar.projectKey=${config.sonarProjectKey} \
                                -Dsonar.host.url=http://34.207.131.166:9000 \
                                -Dsonar.login=${SONAR_TOKEN}
                            """
                        }
                    }
                }
            }

            stage('Check SonarQube Quality Gate') {
                steps {
                    withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                        script {
                            echo "Ensuring jq is installed..."
                            sh "apt-get update && apt-get install -y jq || true"

                            echo "Waiting for SonarQube analysis to complete..."
                            sleep(time: 30, unit: 'SECONDS')

                            def sonarCheck = sh(
                                script: """
                                    curl -s -H "Authorization: Bearer ${SONAR_TOKEN}" \
                                    "http://34.207.131.166:9000/api/qualitygates/project_status?projectKey=${config.sonarProjectKey}" | jq -r .projectStatus.status
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

            stage('Build Docker Image') {
                steps {
                    script {
                        echo "Building Docker image..."
                        sh "docker build -t ${DOCKER_IMAGE}:${IMAGE_TAG} ."
                    }
                }
            }

            stage('Push Docker Image to Docker Hub') {
                steps {
        withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
            script {
                echo "Logging in to Docker Hub..."
                sh "echo ${DOCKER_PASS} | docker login -u ${DOCKER_USER} --password-stdin"

                echo "Tagging the image..."
                sh "docker tag ${DOCKER_IMAGE}:${IMAGE_TAG} ${DOCKER_USER}/${config.imageName}:${IMAGE_TAG}"

                echo "Pushing Docker image..."
                sh "docker push ${DOCKER_USER}/${config.imageName}:${IMAGE_TAG}"
            }
        }
    }
            }

            stage('Run Docker Container') {
                steps {
                    script {
                        echo "Stopping existing container if running..."
                        sh "docker stop my-container || true && docker rm my-container || true"

                        echo "Running Docker container..."
                        sh "docker run -d -p 8080:8080 --name my-container ${DOCKER_IMAGE}:${IMAGE_TAG}"
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
