export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$(cat creds/passwd.txt)"
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat creds/private.asc)"
export ORG_GRADLE_PROJECT_mavenCentralUsername="$(cat creds/maven_passwd.txt)"
export ORG_GRADLE_PROJECT_mavenCentralPassword="$(cat creds/maven_username.txt)"

./gradlew publishToMavenLocal --stacktrace