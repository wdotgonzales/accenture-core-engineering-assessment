# TextScan

A command-line tool that scans a folder tree for a word or regex pattern and reports each hit (file, line, column, preview). It can also write a report file and copy matched files into a timestamped archive. It never modifies the files it scans.

## Clone the code, go to its directory and make the bash script executable

```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment

# This command will make the bash script executable
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

> **Note on expected results:** timestamps and the elapsed time (`finished in ...`) will differ on every run. Everything else should match.

---

## Option 1: Run with Groovy

**Requirements:** JDK 8+, Groovy 2.5+, and internet on the first run (to download Picocli).

### Step 1: Install Groovy

| OS | Command |
| :--- | :--- |
| Windows | `choco install groovy` or `scoop install groovy` |
| macOS | `brew install groovy` |
| Linux | `sdk install groovy` ([SDKMAN](https://sdkman.io/)) or `sudo apt install groovy` |

Reopen your terminal, then check the install:
```bash
java -version
groovy --version
```

### Step 2: Create the test folders

```bash
./setup-test-env.sh
```

This creates `testscan_dir` with the following structure:

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

Running the script again deletes and recreates the folder.

### Step 3: Run the scanner

**Command:**
```bash
groovy TextScan.groovy -d <directory> -p <pattern> [options]
```

**Examples:**

#### 1. Literal search

Finds the exact word `Hello` in every `.txt` file under `testscan_dir`.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello"
```

Expected result:
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
2026-11-02 10:00:00.050 INFO  file1.txt -> 2 hits (line 1)
2026-11-02 10:00:00.050 INFO      line 1, col 1, 14: [[Hello]] world, [[Hello]] again from the root file.
2026-11-02 10:00:00.051 INFO  nested/deep/file3.txt -> 1 hit (line 1)
2026-11-02 10:00:00.051 INFO      line 1, col 26: Deep down inside, we say [[Hello]].
2026-11-02 10:00:00.051 INFO  subdir/file2.txt -> 2 hits (line 2)
2026-11-02 10:00:00.051 INFO      line 2, col 5, 29: Say [[Hello]] to the subfolder! [[Hello]] once more.
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Explanation:
- Each matched file is listed with its hit count and the line numbers.
- The indented lines show the column of each hit and a preview with the match wrapped in `[[ ]]`.
- `app.log`, `.hidden.txt` and `.hidden_folder/secret.txt` do not appear because they are ignored.
- The summary shows 3 files scanned and 5 total hits (2 + 1 + 2).

#### 2. Regex search

Uses a regular expression (`-r`) to match any word that starts with `H` followed by lowercase letters.

```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r
```

Expected result:
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "H[a-z]+" (regex mode)
2026-11-02 10:00:00.050 INFO  file1.txt -> 2 hits (line 1)
2026-11-02 10:00:00.050 INFO      line 1, col 1, 14: [[Hello]] world, [[Hello]] again from the root file.
2026-11-02 10:00:00.051 INFO  nested/deep/file3.txt -> 1 hit (line 1)
2026-11-02 10:00:00.051 INFO      line 1, col 26: Deep down inside, we say [[Hello]].
2026-11-02 10:00:00.051 INFO  subdir/file2.txt -> 2 hits (line 2)
2026-11-02 10:00:00.051 INFO      line 2, col 5, 29: Say [[Hello]] to the subfolder! [[Hello]] once more.
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Explanation:
- The first line says `regex mode` instead of `literal mode`.
- The results match example 1 because `Hello` is the only word in the test files that fits `H[a-z]+`.

#### 3. Report

Does the same scan and also saves the list of matched files to `report.txt`.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report report.txt
```

Expected result (console):
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
... (same file and hit lines as example 1) ...
2026-11-02 10:00:00.051 INFO  Report written to report.txt
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Expected contents of `report.txt`:
```
TextScan report
Folder: testscan_dir
Pattern: Hello (literal)
Generated: 2026-11-02 10:00:00.051

file1.txt	2 hits	line 1
nested/deep/file3.txt	1 hit	line 1
subdir/file2.txt	2 hits	line 2
```

Explanation:
- The console output is the same as example 1, plus a `Report written to report.txt` line.
- `report.txt` lists each matched file with its hit count and line numbers, separated by tabs.

#### 4. Archive

Does the same scan and also copies every matched file into a timestamped backup folder.

```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --archive ./backups
```

Expected result (console):
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
... (same file and hit lines as example 1) ...
2026-11-02 10:00:00.060 INFO  Archived 3 files to /full/path/to/backups/textscan-20261102-100000
2026-11-02 10:00:00.061 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.061 INFO  End: finished in 0.061 s
```

Expected folder created:
```
backups/
└── textscan-20261102-100000/
    ├── file1.txt
    ├── nested/
    │   └── deep/
    │       └── file3.txt
    └── subdir/
        └── file2.txt
```

Explanation:
- Only files with matches are copied, and their folder structure is preserved.
- A new timestamped folder is created on every run, so earlier backups are never overwritten.

---

## Option 2: Run with Docker

**Requirements:** Docker installed and running. No JDK or Groovy needed.

### Step 1: Create the test folders

```bash
./setup-test-env.sh
```

This creates `testscan_dir` with the following structure:

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

Running the script again deletes and recreates the folder.

### Step 2: Run the scanner

**Command:**
```bash
./textscan-docker.sh -d <directory> -p <pattern> [options]
```

The first run builds the image (about a minute). After that it starts immediately. Your current folder is mounted into the container, so all paths are relative to the folder you run the command from, and reports and archives appear on your machine.

You may also see `WARNING: Using incubator modules: jdk.incubator.foreign, jdk.incubator.vector` before the output. It is harmless and comes from the Java version inside the image.

**Examples:**

#### 1. Literal search

Finds the exact word `Hello` in every `.txt` file under `testscan_dir`.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello"
```

Expected result:
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
2026-11-02 10:00:00.050 INFO  file1.txt -> 2 hits (line 1)
2026-11-02 10:00:00.050 INFO      line 1, col 1, 14: [[Hello]] world, [[Hello]] again from the root file.
2026-11-02 10:00:00.051 INFO  nested/deep/file3.txt -> 1 hit (line 1)
2026-11-02 10:00:00.051 INFO      line 1, col 26: Deep down inside, we say [[Hello]].
2026-11-02 10:00:00.051 INFO  subdir/file2.txt -> 2 hits (line 2)
2026-11-02 10:00:00.051 INFO      line 2, col 5, 29: Say [[Hello]] to the subfolder! [[Hello]] once more.
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Explanation:
- Each matched file is listed with its hit count and the line numbers.
- The indented lines show the column of each hit and a preview with the match wrapped in `[[ ]]`.
- `app.log`, `.hidden.txt` and `.hidden_folder/secret.txt` do not appear because they are ignored.
- The summary shows 3 files scanned and 5 total hits (2 + 1 + 2).

#### 2. Regex search

Uses a regular expression (`-r`) to match any word that starts with `H` followed by lowercase letters.

```bash
./textscan-docker.sh -d testscan_dir -p "H[a-z]+" -r
```

Expected result:
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "H[a-z]+" (regex mode)
2026-11-02 10:00:00.050 INFO  file1.txt -> 2 hits (line 1)
2026-11-02 10:00:00.050 INFO      line 1, col 1, 14: [[Hello]] world, [[Hello]] again from the root file.
2026-11-02 10:00:00.051 INFO  nested/deep/file3.txt -> 1 hit (line 1)
2026-11-02 10:00:00.051 INFO      line 1, col 26: Deep down inside, we say [[Hello]].
2026-11-02 10:00:00.051 INFO  subdir/file2.txt -> 2 hits (line 2)
2026-11-02 10:00:00.051 INFO      line 2, col 5, 29: Say [[Hello]] to the subfolder! [[Hello]] once more.
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Explanation:
- The first line says `regex mode` instead of `literal mode`.
- The results match example 1 because `Hello` is the only word in the test files that fits `H[a-z]+`.

#### 3. Report

Does the same scan and also saves the list of matched files to `report.txt` on your machine.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" --report report.txt
```

Expected result (console):
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
... (same file and hit lines as example 1) ...
2026-11-02 10:00:00.051 INFO  Report written to report.txt
2026-11-02 10:00:00.052 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.052 INFO  End: finished in 0.052 s
```

Expected contents of `report.txt`:
```
TextScan report
Folder: testscan_dir
Pattern: Hello (literal)
Generated: 2026-11-02 10:00:00.051

file1.txt	2 hits	line 1
nested/deep/file3.txt	1 hit	line 1
subdir/file2.txt	2 hits	line 2
```

Explanation:
- The console output is the same as example 1, plus a `Report written to report.txt` line.
- `report.txt` appears in the folder you ran the command from, not only inside the container.

#### 4. Archive

Does the same scan and also copies every matched file into a timestamped backup folder on your machine.

```bash
./textscan-docker.sh -d testscan_dir -p "Hello" --archive ./backups
```

Expected result (console):
```
2026-11-02 10:00:00.000 INFO  Start: scanning testscan_dir for "Hello" (literal mode)
... (same file and hit lines as example 1) ...
2026-11-02 10:00:00.060 INFO  Archived 3 files to /work/backups/textscan-20261102-100000
2026-11-02 10:00:00.061 INFO  Summary: 3 files scanned, 3 matched, 5 total hits, 0 errors
2026-11-02 10:00:00.061 INFO  End: finished in 0.061 s
```

Expected folder created:
```
backups/
└── textscan-20261102-100000/
    ├── file1.txt
    ├── nested/
    │   └── deep/
    │       └── file3.txt
    └── subdir/
        └── file2.txt
```

Explanation:
- The log shows `/work/backups/...` because that is the path inside the container. On your machine the folder is `./backups/...`.
- Only files with matches are copied, and their folder structure is preserved.

---

## Scan Behavior

- Only `.txt` files are scanned.
- Hidden files and folders (names starting with `.`) and symbolic links are skipped.
