export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$(cat passwd.txt)"
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat private.asc)"

./gradlew publishToMavenLocal --stacktrace