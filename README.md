# TextScan

A command-line tool that scans a folder tree for a word or regex pattern and prints each hit (file, line, column, preview). It can also write a report file and copy matched files into a timestamped archive. It never modifies the files it scans.

## Clone the code, go to its directory and make the bash scripts executable

```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment

# Make the bash scripts executable
chmod +x setup-test-env.sh textscan-docker.sh
```

> **Windows:** use **Git Bash** or **WSL**. The `.sh` scripts will not run in PowerShell or Command Prompt.

## Options

| Option | Description |
| :--- | :--- |
| `-d`, `--dir` | **(Required)** Folder to scan. |
| `-p`, `--pattern` | **(Required)** Text or regex to search for. |
| `-r`, `--regex` | Treat the pattern as a regular expression. |
| `--report <file>` | Write matched files to a report file. |
| `--archive <dir>` | Copy matched files into a timestamped folder under `<dir>`. |
| `-h`, `--help` | Show help. |

## Scan Behavior

- Only `.txt` files are scanned.
- Hidden files and folders (names starting with `.`) and symbolic links are skipped.

---

## Option 1: Run with Groovy

**Requirements:** JDK 8+, Groovy 2.5+, and internet on the first run (to download Picocli).

### Step 1: Install Java (JDK)

Skip this step if `java -version` already shows version 8 or higher.

| OS | Command |
| :--- | :--- |
| Windows | `choco install temurin17` or `winget install EclipseAdoptium.Temurin.17.JDK` |
| macOS | `brew install --cask temurin@17` |
| Linux (Debian/Ubuntu) | `sudo apt update && sudo apt install openjdk-17-jdk` |
| Linux (any distro) | `sdk install java` ([SDKMAN](https://sdkman.io/)) |

Reopen your terminal, then check the install:

```bash
java -version
```

You should see a version of 8 or higher (for example, `openjdk version "17.0.x"`).

> You can also download a JDK installer directly from [Adoptium](https://adoptium.net/).

### Step 2: Install Groovy

| OS | Command |
| :--- | :--- |
| Windows | `choco install groovy` or `scoop install groovy` |
| macOS | `brew install groovy` |
| Linux | `sdk install groovy` ([SDKMAN](https://sdkman.io/)) or `sudo apt install groovy` |

Reopen your terminal, then check the install:

```bash
groovy --version
```

### Step 3: Create the test folders

```bash
./setup-test-env.sh
```

This creates `testscan_dir` with the following structure. Running the script again deletes and recreates the folder.

```
testscan_dir/
├── file1.txt                  <- scanned (2 hits for "Hello")
├── app.log                    <- ignored (not a .txt file)
├── .hidden.txt                <- ignored (hidden file)
├── .hidden_folder/            <- ignored (hidden folder)
│   └── secret.txt
├── subdir/
│   └── file2.txt              <- scanned (2 hits for "Hello")
└── nested/
    └── deep/
        └── file3.txt          <- scanned (1 hit for "Hello")
```

### Step 4: Run the scanner

```bash
groovy TextScan.groovy -d <directory> -p <pattern> [options]
```

Timestamps and the elapsed time in the output will be different on every run.

#### 1. Literal search

Looks for the exact word `Hello` in every `.txt` file under `testscan_dir`. You should get 5 hits across 3 files.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello"
```

#### 2. Regex search

Same search, but with `-r` so the pattern is read as a regex. `H[a-z]+` matches any word starting with `H` followed by lowercase letters, which here is still just `Hello`, so the results are the same as the literal search.

```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r
```

#### 3. Report

Runs the same scan and also saves the matched files, with their hit counts and line numbers, to `report.txt`.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report report.txt
```

#### 4. Archive

Runs the same scan and also copies the matched files into a new timestamped folder under `./backups`, keeping their folder structure. Every run makes a new folder, so older backups are never overwritten.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --archive ./backups
```

---

## Option 2: Run with Docker

**Requirements:** Docker installed and running. No JDK or Groovy needed.

### Step 1: Create the test folders

```bash
./setup-test-env.sh
```

This creates the same `testscan_dir` structure shown in Option 1.

### Step 2: Run the scanner

```bash
./textscan-docker.sh -d <directory> -p <pattern> [options]
```

The first run builds the image, which takes about a minute. After that it starts right away. Your current folder is mounted into the container, so paths are relative to where you run the command, and any reports or archives show up on your machine.

You might see `WARNING: Using incubator modules: jdk.incubator.foreign, jdk.incubator.vector` before the output. It's harmless and comes from the Java version inside the image.

#### 1. Literal search

Looks for the exact word `Hello` in every `.txt` file under `testscan_dir`. You should get 5 hits across 3 files.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello"
```

#### 2. Regex search

Same search with `-r`, so `H[a-z]+` is treated as a regex. It still only matches `Hello` in the test files, so the results are the same.

```bash
./textscan-docker.sh -d testscan_dir -p "H[a-z]+" -r
```

#### 3. Report

Runs the same scan and saves the matched files to `report.txt` in the folder you ran the command from.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" --report report.txt
```

#### 4. Archive

Runs the same scan and copies the matched files into a new timestamped folder under `./backups` on your machine. The log shows `/work/backups/...` because that's the path inside the container.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" --archive ./backups
```
