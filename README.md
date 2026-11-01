# TextScan

A command-line tool that scans a folder tree for a word or regex pattern and reports every hit: file, line, column, and a preview of the matching line. It can also write a report file and copy matched files into a timestamped archive. It never modifies the files it scans.

## Table of Contents

- [Ways to Run](#ways-to-run)
- [Option A: Native Install](#option-a-native-install)
- [Option B: Docker](#option-b-docker)
- [Quick Start & Test Environment](#quick-start--test-environment)
- [Usage & Command-Line Options](#usage--command-line-options)
- [Examples](#examples)
- [Scan Behavior](#scan-behavior)
- [Troubleshooting](#troubleshooting)

---

## Ways to Run

| | Option A: Native | Option B: Docker |
| :--- | :--- | :--- |
| Needs | JDK 8+, Groovy 2.5+, internet on first run | Docker only |
| Command | `groovy TextScan.groovy ...` | `./textscan-docker.sh ...` |
| Best for | Quick runs if Groovy is installed | A guaranteed identical environment on any machine |

All parameters are passed as command-line arguments in both options. Nothing is hardcoded.

A bash shell is needed for `setup-test-env.sh` and `textscan-docker.sh` (Git Bash or WSL on Windows).

---

## Option A: Native Install

### 1. Install a JDK

```bash
java -version
```

If not found, install JDK 8 or higher (for example, [Eclipse Temurin](https://adoptium.net/)).

### 2. Install Groovy (2.5 or higher)

**Windows**

```powershell
choco install groovy
```
or
```powershell
scoop install groovy
```
Manual: download the binary from <https://groovy.apache.org/download.html>, unzip it, set `GROOVY_HOME`, add `%GROOVY_HOME%\bin` to `PATH`, then **reopen your terminal (including Git Bash)**.

**macOS**

```bash
brew install groovy
```

**Linux**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install groovy
```
Or `sudo apt install groovy` on Debian/Ubuntu (may be an older version).

### 3. Verify

```bash
java -version
groovy --version
```

The first run downloads Picocli through `@Grab`, so it needs internet access once.

---

## Option B: Docker

Requires [Docker](https://docs.docker.com/get-docker/) to be installed and running. No JDK or Groovy needed.

```bash
chmod +x setup-test-env.sh textscan-docker.sh
./setup-test-env.sh
./textscan-docker.sh -d testscan_dir -p "Hello"
```

The first run builds the image, which takes a minute. After that it starts immediately. The Picocli dependency is baked into the image, so scans need no internet.

How it works:
- The script runs `docker run --rm`, so a fresh container starts for each scan and is deleted afterward.
- Your **current folder** is mounted into the container as `/work`.
- `-d`, `--report`, and `--archive` paths are relative to your current folder, so reports and archives appear on your machine.
- **Paths outside the current folder are not visible to the container.** Run the script from a folder that contains everything you need.

Without the wrapper script:

```bash
docker build -t textscan .
docker run --rm -v "$(pwd):/work" textscan -d testscan_dir -p "Hello"
```

On Linux, files the container creates (reports, archives) may be owned by a different user than you. If that matters, use `sudo chown` on them.

---

## Quick Start & Test Environment

Clone the repository:
```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment
```

> **Windows users:** run these commands in **Git Bash** or **WSL**. The helper scripts are bash and will not run in PowerShell or Command Prompt.

1. **Generate the test directory:**
   ```bash
   chmod +x setup-test-env.sh
   ./setup-test-env.sh
   ```
   *(Creates `testscan_dir` with sample files, subfolders, and hidden items. Running it again deletes and recreates that folder.)*

2. **Run a basic search:**
   ```bash
   # Native
   groovy TextScan.groovy -d testscan_dir -p "Hello"

   # Docker
   ./textscan-docker.sh -d testscan_dir -p "Hello"
   ```

---

## Usage & Command-Line Options

```bash
groovy TextScan.groovy -d <directory> -p <pattern> [options]
```
With Docker, replace `groovy TextScan.groovy` with `./textscan-docker.sh`.

| Option | Long Option | Description |
| :--- | :--- | :--- |
| `-d` | `--dir` | **(Required)** Root folder path to scan. |
| `-p` | `--pattern` | **(Required)** Text string or regular expression to search for. |
| `-r` | `--regex` | Treat the pattern as a regular expression. |
| | `--report <file>` | Write the list of matched files and hit details to a file. |
| | `--archive <dir>` | Copy matched files into a timestamped subfolder under `<dir>`. |
| `-h` | `--help` | Display help usage information and exit. |

---

## Examples

The examples use the native command. For Docker, swap in `./textscan-docker.sh`.

### 1. Basic Literal Search
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello"
```

### 2. Regular Expression Search
Words starting with `H` followed by lowercase letters:
```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r
```

### 3. Generate a Report File
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report report.txt
```

### 4. Archive Matched Files
Copy matched files into a timestamped folder under `./backups`:
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --archive ./backups
```

### 5. Combined Report and Archive
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report audit_report.txt --archive ./my_archives
```

