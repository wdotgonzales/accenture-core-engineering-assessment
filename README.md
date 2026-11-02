# TextScan

A command-line tool that finds a word or regex pattern in the `.txt` files of a folder tree and prints each hit (file, line, column, preview). If you give it new text with `-n`, it also **replaces every occurrence**. It can back up the matched files before changing them and write a report file.

Without `-n`, TextScan only searches and **never modifies your files**.

## Clone the code, go to its directory and make the bash scripts executable

```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment

# Make the bash scripts executable
chmod +x setup-test-env.sh textscan-docker.sh
```

> **Windows:** use **Git Bash** or **WSL**. The `.sh` scripts will not run in PowerShell or Command Prompt.

## Options

| Option            | Description                                                                                                            |
| :---------------- | :--------------------------------------------------------------------------------------------------------------------- |
| `-d`, `--dir`     | **(Required)** Folder to search. Subfolders are included.                                                              |
| `-p`, `--pattern` | **(Required)** Text or regex to search for.                                                                            |
| `-n`, `--new`     | *(Optional)* Text that replaces every match. Use `""` to delete the matches. Leave it out to only search.             |
| `-r`, `--regex`   | Treat the pattern as a regular expression. The new text can then use group references (`$1`, `$2`).                    |
| `--report <file>` | Write a report file. With `-n` it lists the modified files; without `-n` it lists the matched files.                  |
| `--archive <dir>` | Copy the matched files into a timestamped folder under `<dir>`. With `-n`, this happens **before** they are modified.  |
| `-h`, `--help`    | Show help.                                                                                                             |

All values come from the command line. Nothing is hardcoded.

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
groovy TextScan.groovy -d <directory> -p <pattern> [-n <new text>] [options]
```

Timestamps and the elapsed time in the output will be different on every run.

> Examples 1 to 3 only search, so they are safe to run in any order. Examples 4 to 8 use `-n` and change the test files. Run `./setup-test-env.sh` again to reset them before trying the next one.

#### 1. Search only (literal)

Looks for the exact word `Hello` in every `.txt` file under `testscan_dir`. You should get 5 hits across 3 files. No file is changed.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello"
```

#### 2. Search only (regex)

Same search, but with `-r` so the pattern is read as a regex. `H[a-z]+` matches any word starting with `H` followed by lowercase letters, which here is still just `Hello`.

```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r
```

#### 3. Search with report or archive

Without `-n`, `--report` lists the matched files with their hit counts and line numbers, and `--archive` copies the matched files into a timestamped folder. No file is changed.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report report.txt
groovy TextScan.groovy -d testscan_dir -p "Hello" --archive ./backups
```

#### 4. Literal replace

Adds `-n` to replace every `Hello` with `Hi`. You should get 5 replacements across 3 files.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi"
```

#### 5. Regex replace

```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r -n "Hi"
```

With a capture group (use single quotes so the shell doesn't expand `$1`). This turns `Hello` into `Hi`:

```bash
groovy TextScan.groovy -d testscan_dir -p "(H)ello" -r -n '$1i'
```

#### 6. Report

Runs the replace and saves the modified files, with their replacement counts and line numbers, to `report.txt`.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi" --report report.txt
```

#### 7. Backup

Copies the original files into a new timestamped folder under `./backups` (keeping their folder structure) before changing them. Every run makes a new folder, so older backups are never overwritten.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" -n "Hi" --archive ./backups
```

#### 8. Everything together

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
./textscan-docker.sh -d <directory> -p <pattern> [-n <new text>] [options]
```

The first run builds the image, which takes about a minute. After that it starts right away. Your current folder is mounted into the container, so paths are relative to where you run the command, and any modified files, reports, or backups show up on your machine.

You might see `WARNING: Using incubator modules: jdk.incubator.foreign, jdk.incubator.vector` before the output. It's harmless and comes from the Java version inside the image.

> Examples 1 and 2 only search. Examples 3 to 7 use `-n` and change the test files. Run `./setup-test-env.sh` again to reset them.

#### 1. Search only (literal)

```bash
./textscan-docker.sh -d testscan_dir -p "Hello"
```

#### 2. Search only (regex)

```bash
./textscan-docker.sh -d testscan_dir -p "H[a-z]+" -r
```

#### 3. Literal replace

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi"
```

#### 4. Regex replace

```bash
./textscan-docker.sh -d testscan_dir -p "H[a-z]+" -r -n "Hi"
```

#### 5. Report

Saves the list of modified files to `report.txt` in the folder you ran the command from. Without `-n`, it lists the matched files instead.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --report report.txt
```

#### 6. Backup

Copies the originals into a new timestamped folder under `./backups` on your machine before changing them. The log shows `/work/backups/...` because that's the path inside the container.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --archive ./backups
```

#### 7. Everything together

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" -n "Hi" --archive ./backups --report report.txt
```
