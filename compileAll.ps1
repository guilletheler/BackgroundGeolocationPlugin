$ORIGINAL_JAVA_HOME = [System.Environment]::GetEnvironmentVariable("JAVA_HOME")

[System.Environment]::SetEnvironmentVariable("JAVA_HOME", [System.Environment]::GetEnvironmentVariable("JAVA21_HOME"))

pnpm run build

pnpm run verify:android

[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $ORIGINAL_JAVA_HOME)