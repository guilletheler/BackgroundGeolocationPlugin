$ORIGINAL_JAVA_HOME = [System.Environment]::GetEnvironmentVariable("JAVA_HOME")

[System.Environment]::SetEnvironmentVariable("JAVA_HOME", [System.Environment]::GetEnvironmentVariable("JAVA21_HOME"))

pnpm cap build android
pnpm cap sync

[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $ORIGINAL_JAVA_HOME)