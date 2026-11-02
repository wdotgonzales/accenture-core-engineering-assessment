# TextScan

A command-line tool that finds a word or regex pattern in the `.txt` files of a folder tree and **replaces every occurrence with new text**. Each hit is printed (file, line, column, preview). It can also back up the original files before changing them and write a report listing the files that were modified.

## Clone the code, go to its directory and make the bash scripts executable

```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment

# Make the bash scripts executable
chmod +x setup-test-env.sh textscan-docker.sh
```

> **Windows:** use **Git Bash** or **WSL**. The `.sh` scripts will not run in PowerShell or Command Prompt.

## Options

| Option            | Description                                                                                          |
| :---------------- | :--------------------------------------------------------------------------------------------------- |
| `-d`, `--dir`     | **(Required)** Folder to search. Subfolders are included.                                            |
| `-p`, `--pattern` | **(Required)** Text or regex to search for.                                                          |
| `-n`, `--new`     | **(Required)** Text that replaces every match. Use `""` to delete the matches.                       |
| `-r`, `--regex`   | Treat the pattern as a regular expression. The new text can then use group references (`$1`, `$2`).  |
| `--report <file>` | Write the list of modified files (with replacement counts and line numbers) to a file.               |
| `--archive <dir>` | Copy the original files into a timestamped folder under `<dir>` **before** they are modified.        |
| `-h`, `--help`    | Show help.                                                                                           |

All values come from the command line. Nothing is hardcoded.

## Scan and Replace Behavior

- **Files are modified in place.** Use `--archive` if you want a copy of the originals.
- Only `.txt` files are processed, in the given folder and all its subfolders.
- Hidden files and folders (names starting with `.`) and symbolic links are skipped.
- The report file and the archive folder are never scanned, even if they sit inside the scanned folder.
- In literal mode, `$` and `\` in the new text are plain characters. In regex mode, `$1`, `$2`, and so on refer to capture groups.
- Line endings (`\n` or `\r\n`) are kept as they were.
- Files are read and written as UTF-8.
- Each file is written to a temporary file first and then moved over the original, so a crash can't leave a half-written file.
- If `--archive` is used and a file's backup fails, that file is **not** modified.
- Empty-matching patterns (for example `a*`) are rejected.

Each run logs the start time, every match (file, line, column), each modified file, any errors, a summary, and the end time with the elapsed duration.

---

## Option 1: Run with Groovy

**Requirements:** JDK 8+, Groovy 2.5+, and internet on the first run (to download Picocli).

### Step 1: Install Java (JDK)

Skip this step if `java -version` already shows version 8 or higher.

| OS                    | Command                                                                      |
| :-------------------- | :--------------------------------------------------------------------------- |
| Windows               | `choco install temurin17` or `winget install EclipseAdoptium.Temurin.17.JDK` |
| macOS                 | `brew install --cask temurin@17`                                             |
| Linux (Debian/Ubuntu) | `sudo apt update && sudo apt install openjdk-17-jdk`                         |
| Linux (any distro)    | `sdk install java` ([SDKMAN](https://sdkman.io/))                            |

Reopen your terminal, then check the install:

```bash
java -version
```

### Step 2: Install Groovy

| OS      | Command                                                                          |
| :------ | :------------------------------------------------------------------------------- |
| Windows | `choco install groovy` or `scoop install groovy`                                 |
| macOS   | `brew install groovy`                                                            |
| Linux   | `sdk install groovy` ([SDKMAN](https://sdkman.io/)) or `sudo apt install groovy` |

Reopen your terminal, then check the install:

```bash
groovy --version
```

### Step 3: Create the test folders

```bash
./setup-test-env.sh
```

This creates `testscan_dir` with the following structure. Running the script again deletes and recreates the folder, which is how you reset the test files after a replace.

```
testscan_dir/
├── file1.txt                  <- processed (2 hits for "Hello")
├── app.log                    <- ignored (not a .txt file)
├── .hidden.txt                <- ignored (hidden file)
├── .hidden_folder/            <- ignored (hidden folder)
│   └── secret.txt
├── subdir/
│   └── file2.txt              <- processed (2 hits for "Hello")
└── nested/
    └── deep/
        └── file3.txt          <- processed (1 hit for "Hello")
```

### Step 4: Run the tool

```bash
groovy TextScan.groovy -d <directory> -p <pattern> -n <new text> [options]
```

Timestamps and the elapsed time in the output will be different on every run.

> The test files are changed by every example below. Run `./setup-test-env.sh` again to reset them before trying the next one.

#### 1. Literal replace

Replaces every `Hello` in every `.txt` file under `testscan_dir` with `Hi`. You should get 5 replacements across 3 files.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi"
```

#### 2. Regex replace

Same replacement, but with `-r` so the pattern is read as a regex. `H[a-z]+` matches any word starting with `H` followed by lowercase letters, which here is still just `Hello`.

```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r -n "Hi"
```

With a capture group (use single quotes so the shell doesn't expand `$1`). This turns `Hello` into `Hi`:

```bash
groovy TextScan.groovy -d testscan_dir -p "(H)ello" -r -n '$1i'
```

#### 3. Report

Runs the replace and saves the modified files, with their replacement counts and line numbers, to `report.txt`.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi" --report report.txt
```

#### 4. Backup

Copies the original files into a new timestamped folder under `./backups` (keeping their folder structure) before changing them. Every run makes a new folder, so older backups are never overwritten.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi" --archive ./backups
```

#### 5. Everything together

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi" --archive ./backups --report report.txt
```

---

## Option 2: Run with Docker

**Requirements:** Docker installed and running. No JDK or Groovy needed.

### Step 1: Create the test folders

```bash
./setup-test-env.sh
```

This creates the same `testscan_dir` structure shown in Option 1.

### Step 2: Run the tool

```bash
./textscan-docker.sh -d <directory> -p <pattern> -n <new text> [options]
```

The first run builds the image, which takes about a minute. After that it starts right away. Your current folder is mounted into the container, so paths are relative to where you run the command, and any modified files, reports, or backups show up on your machine.

You might see `WARNING: Using incubator modules: jdk.incubator.foreign, jdk.incubator.vector` before the output. It's harmless and comes from the Java version inside the image.

#### 1. Literal replace

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi"
```

#### 2. Regex replace

```bash
./textscan-docker.sh -d testscan_dir -p "H[a-z]+" -r -n "Hi"
```

#### 3. Report

Saves the list of modified files to `report.txt` in the folder you ran the command from.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --report report.txt
```

#### 4. Backup

Copies the originals into a new timestamped folder under `./backups` on your machine before changing them. The log shows `/work/backups/...` because that's the path inside the container.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --archive ./backups
```

#### 5. Everything together

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --archive ./backups --report report.txt
```
