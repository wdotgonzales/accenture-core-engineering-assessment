# TextScan

A command-line tool that scans a folder tree for a word or regex pattern and reports every hit: file, line, column, and a preview of the matching line. It can also write a report file and copy matched files into a timestamped archive. It never modifies the files it scans.

## Requirements

- **Groovy** (with `@Grab` support for Picocli dependency management)
- **Java Development Kit (JDK)** (Java 8 or higher)

---

## Quick Start & Test Environment

### Prerequisite
Clone the repository and go to the directory.
```bash
git clone https://github.com/wdotgonzales/accenture-core-engineering-assessment.git
cd accenture-core-engineering-assessment
```

To test the script, use the provided bash script to generate a test directory tree containing sample `.txt` files, subdirectories, and hidden items.

1. **Make the test setup script executable & run it:**
   ```bash
   chmod +x setup-test-env.sh
   ./setup-test-env.sh
   ```
   *(This creates a folder named `testscan_dir` populated with test files.)*

2. **Run a basic search:**
   ```bash
   groovy TextScan.groovy -d testscan_dir -p "Hello"
   ```

---

## Usage & Command-Line Options

```bash
groovy TextScan.groovy -d <directory> -p <pattern> [options]
```

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

### 1. Basic Literal Search
Search for the literal word `"Hello"` inside `testscan_dir`:
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello"
```

### 2. Regular Expression Search
Search for words starting with `H` followed by lowercase letters using regex mode (`-r`):
```bash
groovy TextScan.groovy -d testscan_dir -p "H[a-z]+" -r
```

### 3. Generate a Report File
Scan a directory and save the results to `report.txt`:
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report report.txt
```

### 4. Archive Matched Files
Scan a directory and mirror all matched files into a timestamped backup folder under `./backups`:
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --archive ./backups
```

### 5. Combined Report and Archive Execution
Run a full audit generating both a report file and an archived tree backup:
```bash
groovy TextScan.groovy -d testscan_dir -p "Hello" --report audit_report.txt --archive ./my_archives
```
