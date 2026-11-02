@GrabConfig(systemClassLoader = true)
@Grab('info.picocli:picocli:4.7.6')
import groovy.cli.picocli.CliBuilder
import groovy.io.FileType
import groovy.io.FileVisitResult
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Text search and replace tool. Looks through a folder and its subfolders for a word or
 * regex pattern and prints every hit. If a new text is given (-n), it also replaces every
 * occurrence. Can optionally copy the matched files into a backup folder first and write
 * a report. Without -n, no file is ever modified.
 */
class TextScan {
    // Always read and write files as UTF-8 so character encoding doesn't break
    static final String CHARSET = 'UTF-8'

    // Date formats for logs and archive folders
    static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss.SSS')
    static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern('yyyyMMdd-HHmmss')

    String rootDir
    String pattern
    String newText          // null when -n was not given (search only)
    String replacement      // text as passed to Matcher.replaceAll (escaped in literal mode)
    boolean replaceMode     // true only when -n was given
    boolean regex
    String reportFile
    String archiveDir

    // Compile the pattern once so we don't recompile it for every file/line
    Pattern compiledPattern

    // Track errors that happen outside of file scanning, like report writing failures
    int extraErrors = 0

    /**
     * Main run method. Checks inputs, scans files, backs up originals, replaces text
     * (only when -n was given), and handles optional report generation.
     */
    boolean run() {
        Instant start = Instant.now()

        // Check if the root directory exists and is readable
        if (!isDirValid(rootDir)) {
            System.err.println "Error: '${rootDir}' does not exist, is not a directory, or cannot be read."
            return false
        }

        // Check output file and folder paths before scanning
        String outputProblem = validateOutputs()
        if (outputProblem) {
            System.err.println "Error: ${outputProblem}"
            return false
        }

        if (replaceMode) {
            log('INFO', "Start: replacing \"${pattern}\" with \"${newText}\" in ${rootDir} (${regex ? 'regex' : 'literal'} mode)")
        } else {
            log('INFO', "Start: searching for \"${pattern}\" in ${rootDir} (${regex ? 'regex' : 'literal'} mode)")
        }

        // Find matches and print them
        List results = scan()
        report(results)

        // Back up the originals BEFORE touching them, then replace the text (only if -n was given)
        if (archiveDir) { archive(results) }
        if (replaceMode) { replaceFiles(results) }

        // Write the list of modified (or matched) files if requested
        if (reportFile) { writeReport(results) }

        // Print final summary and elapsed time
        summary(results)
        log('INFO', "End: finished in ${formatDuration(Duration.between(start, Instant.now()))}")
        return true
    }

    /**
     * Compiles search text into a Pattern.
     * Uses Pattern.quote for literal mode so special characters don't break regex.
     */
    static Pattern buildPattern(String text, boolean asRegex) {
        return asRegex ? Pattern.compile(text) : Pattern.compile(Pattern.quote(text))
    }

    /**
     * Checks if a path is a valid and readable directory.
     */
    boolean isDirValid(String path) {
        if (!path) { return false }
        def dir = new File(path)
        return dir.isDirectory() && dir.canRead()
    }

    /**
     * Validates report and archive paths to prevent errors later.
     */
    String validateOutputs() {
        if (reportFile) {
            Path report = Paths.get(reportFile).toAbsolutePath().normalize()
            if (Files.isDirectory(report)) {
                return "--report '${reportFile}' is a folder; please provide a file path."
            }
            Path parent = report.parent
            if (Files.exists(parent) && !Files.isWritable(parent)) {
                return "Cannot write the report into '${parent}'."
            }
        }
        if (archiveDir) {
            Path archive = Paths.get(archiveDir).toAbsolutePath().normalize()
            if (Files.exists(archive) && !Files.isDirectory(archive)) {
                return "--archive '${archiveDir}' exists and is not a folder."
            }
            if (Files.exists(archive) && !Files.isWritable(archive)) {
                return "Cannot write into the archive folder '${archiveDir}'."
            }
        }
        return null
    }

    /**
     * Replaces every match in each matched file. If a backup folder was requested, a file is
     * only modified when its original was backed up successfully.
     */
    void replaceFiles(List results) {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize()

        results.findAll { it.hits && !it.error }.each { r ->
            if (archiveDir && !r.backedUp) {
                // Never overwrite a file we promised to back up but couldn't
                r.error = 'skipped, original could not be backed up'
                log('ERROR', "${r.file} -> not modified (backup failed)")
                return
            }
            try {
                replaceInFile(root.resolve(r.relPath))
                r.modified = true
                log('INFO', "Modified ${r.file}: ${r.hits} ${plural(r.hits, 'replacement')}")
            } catch (Exception e) {
                r.error = e.message ?: e.class.simpleName
                log('ERROR', "Could not modify ${r.file}: ${r.error}")
            }
        }
    }

    /**
     * Rewrites one file with the replacement applied. The new content goes to a temp file in
     * the same folder first, then replaces the original, so a crash can't leave a half-written file.
     * Works line by line so line endings (\n or \r\n) are kept exactly as they were.
     */
    void replaceInFile(Path file) {
        String content = new String(Files.readAllBytes(file), CHARSET)
        StringBuilder out = new StringBuilder(content.length())

        // Split after every \n so each piece keeps its own line ending
        content.split('(?<=\n)', -1).each { String segment ->
            String eol = segment.endsWith('\r\n') ? '\r\n' : (segment.endsWith('\n') ? '\n' : '')
            String body = segment.substring(0, segment.length() - eol.length())
            out << compiledPattern.matcher(body).replaceAll(replacement) << eol
        }

        Path temp = Files.createTempFile(file.parent, '.textscan-', '.tmp')
        try {
            Files.write(temp, out.toString().getBytes(CHARSET))
            // Keep the original file permissions when the file system supports it
            try {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file))
            } catch (Exception ignored) { /* non-POSIX file system, keep defaults */ }

            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /**
     * Writes the report file using a temp file for safety. In replace mode it lists the
     * modified files; in search-only mode it lists the matched files.
     */
    void writeReport(List results) {
        Path target = Paths.get(reportFile).toAbsolutePath().normalize()
        Path temp = null
        try {
            Files.createDirectories(target.parent)
            temp = Files.createTempFile(target.parent, '.textscan-', '.tmp')

            String nl = System.lineSeparator()
            // Replace mode lists modified files; search-only mode lists matched files
            List listed = replaceMode ? results.findAll { it.modified }
                                      : results.findAll { it.hits && !it.error }
            String unit = replaceMode ? 'replacement' : 'hit'
            temp.toFile().withWriter(CHARSET) { Writer w ->
                w << "TextScan report${nl}"
                w << "Folder: ${rootDir}${nl}"
                w << "Pattern: ${pattern} (${regex ? 'regex' : 'literal'})${nl}"
                if (replaceMode) { w << "Replaced with: ${newText}${nl}" }
                w << "Generated: ${LocalDateTime.now().format(LOG_TIME)}${nl}"
                w << "${replaceMode ? 'Files modified' : 'Files matched'}: ${listed.size()}${nl}${nl}"
                listed.each { r ->
                    w << "${r.file}\t${r.hits} ${plural(r.hits, unit)}\t${lineLabel(r.lines)}${nl}"
                }
            }

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            log('INFO', "Report written to ${reportFile}")
        } catch (Exception e) {
            extraErrors++
            log('ERROR', "Could not write report '${reportFile}': ${e.message}")
            if (temp != null) { Files.deleteIfExists(temp) }
        }
    }

    /**
     * Copies the ORIGINAL matched files into a timestamped folder, preserving folder structure.
     * Marks each successfully copied file so replaceFiles knows it is safe to modify.
     */
    void archive(List results) {
        List matched = results.findAll { it.hits && !it.error }
        if (!matched) {
            log('INFO', 'Backup skipped: no matched files to copy.')
            return
        }

        Path root = Paths.get(rootDir).toAbsolutePath().normalize()
        Path runFolder = newArchiveFolder()
        int copied = 0

        matched.each { r ->
            try {
                Path target = runFolder.resolve(r.relPath)
                Files.createDirectories(target.parent)
                Files.copy(root.resolve(r.relPath), target, StandardCopyOption.COPY_ATTRIBUTES)
                r.backedUp = true
                copied++
            } catch (Exception e) {
                extraErrors++
                log('ERROR', "Could not back up ${r.file}: ${e.message}")
            }
        }
        log('INFO', "Backed up ${copied} ${plural(copied, 'file')} to ${runFolder}")
    }

    /**
     * Creates a unique archive folder based on the current timestamp.
     */
    Path newArchiveFolder() {
        Path base = Paths.get(archiveDir).toAbsolutePath().normalize()
        String name = "textscan-${LocalDateTime.now().format(FOLDER_TIME)}"
        Path folder = base.resolve(name)
        int n = 2
        while (Files.exists(folder)) {
            folder = base.resolve("${name}-${n++}".toString())
        }
        Files.createDirectories(folder)
        return folder
    }

    /**
     * Reads a single file line by line to save memory, finding matches and formatting previews.
     */
    Map searchFile(File file) {
        int count = 0
        List lines = []

        file.eachLine(CHARSET) { String line, int lineNumber ->
            Matcher m = compiledPattern.matcher(line)
            StringBuilder preview = new StringBuilder()
            List columns = []

            while (m.find()) {
                columns << m.start() + 1
                m.appendReplacement(preview, Matcher.quoteReplacement("[[${m.group()}]]".toString()))
            }

            if (columns) {
                m.appendTail(preview)
                count += columns.size()
                lines << [line: lineNumber, columns: columns, preview: preview.toString()]
            }
        }

        return [count: count, lines: lines]
    }

    /**
     * Walks through the directory tree to find text files while ignoring hidden files, symlinks,
     * the report file and the backup folder.
     */
    List scan() {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize()
        Path archiveRoot = archiveDir ? Paths.get(archiveDir).toAbsolutePath().normalize() : null
        Path reportPath = reportFile ? Paths.get(reportFile).toAbsolutePath().normalize() : null

        List files = []

        root.toFile().traverse(
            type: FileType.FILES,
            nameFilter: ~/.*\.txt$/,
            preDir: { File dir ->
                Path dirPath = dir.toPath()
                if (dir.name.startsWith('.') || Files.isSymbolicLink(dirPath) ||
                        (archiveRoot != null && dirPath.toAbsolutePath().normalize() == archiveRoot)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        ) { File file ->
            Path filePath = file.toPath()
            if (!file.name.startsWith('.') && !Files.isSymbolicLink(filePath) &&
                    filePath.toAbsolutePath().normalize() != reportPath) {
                files << file
            }
        }

        return files.collect { File file ->
            Path relPath = root.relativize(file.toPath().toAbsolutePath().normalize())
            Map result = [file: relPath.toString(), relPath: relPath, hits: 0, lines: [],
                          error: null, backedUp: false, modified: false]

            try {
                Map found = searchFile(file)
                result.hits = found.count
                result.lines = found.lines
            } catch (Exception e) {
                result.error = e.message ?: e.class.simpleName
            }
            return result
        }.sort { it.file }
    }

    /**
     * Prints search results to the console.
     */
    void report(List results) {
        results.each { r ->
            if (r.error) {
                log('ERROR', "${r.file} -> could not read file: ${r.error}")
            } else if (r.hits) {
                log('INFO', "${r.file} -> ${r.hits} ${plural(r.hits, 'hit')} (${lineLabel(r.lines)})")
                r.lines.each { l ->
                    log('INFO', "    line ${l.line}, col ${l.columns.join(', ')}: ${l.preview}")
                }
            } else {
                log('INFO', "${r.file} -> (no match)")
            }
        }
    }

    /**
     * Prints a summary of files scanned, matched, modified (replace mode) or hits (search mode),
     * and total errors.
     */
    void summary(List results) {
        int scanned = results.size()
        int matched = results.count { it.hits }
        int modified = results.count { it.modified }
        int replaced = results.sum(0) { it.modified ? it.hits : 0 }
        int hits = results.sum(0) { it.hits }
        int errors = results.count { it.error } + extraErrors

        String text = "Summary: ${scanned} ${plural(scanned, 'file')} scanned, ${matched} matched"
        if (replaceMode) {
            text += ", ${modified} modified, ${replaced} ${plural(replaced, 'replacement')}"
        } else {
            text += ", ${hits} ${plural(hits, 'hit')}"
        }
        text += ", ${errors} ${plural(errors, 'error')}"
        log(errors ? 'WARN' : 'INFO', text)
    }

    /**
     * Simple logging helper with timestamps. Info goes to stdout, warnings/errors go to stderr.
     */
    void log(String level, String message) {
        String line = "${LocalDateTime.now().format(LOG_TIME)} ${level.padRight(5)} ${message}"
        if (level == 'INFO') {
            println line
        } else {
            System.err.println line
        }
    }

    static String plural(int n, String word) {
        return n == 1 ? word : "${word}s"
    }

    static String lineLabel(List lines) {
        List numbers = lines*.line
        return "${numbers.size() == 1 ? 'line' : 'lines'} ${numbers.join(', ')}"
    }

    static String formatDuration(Duration d) {
        return String.format('%.3f s', d.toMillis() / 1000.0)
    }

    /**
     * Entry point. Parses arguments using Picocli and starts the run.
     */
    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'groovy TextScan.groovy -d <directory> -p <pattern> [-n <new text>] [options]')
        cli.d(names: ['-d', '--dir'], args: 1, required: true, argName: 'directory', 'Folder to scan')
        cli.p(names: ['-p', '--pattern'], args: 1, required: true, argName: 'pattern', 'Text to search for')
        cli.n(names: ['-n', '--new'], args: 1, argName: 'new text', 'Optional. Text that replaces every match (omit to only search)')
        cli.r(names: ['-r', '--regex'], 'Treat the pattern as a regular expression (new text may use $1, $2...)')
        cli._(names: ['--report'], args: 1, argName: 'file', 'Write the list of modified (or matched) files to this file')
        cli._(names: ['--archive'], args: 1, argName: 'directory', 'Copy matched files into this folder (before replacing, if -n is used)')
        cli.h(names: ['-h', '--help'], usageHelp: true, 'Show this help and exit')

        def options = cli.parse(args)
        if (!options) { System.exit(1) }

        if (options.h) {
            cli.usage()
            System.exit(0)
        }

        boolean asRegex = options.r ? true : false
        // A missing -n does not come back as a String, a given -n (even "") does
        boolean replaceMode = options.n instanceof String
        String newText = replaceMode ? options.n.toString() : null

        Pattern compiled
        try {
            compiled = buildPattern(options.p, asRegex)
        } catch (PatternSyntaxException e) {
            System.err.println "Error: invalid regex '${options.p}': ${e.description}"
            cli.usage()
            System.exit(1)
        }

        // Prevent empty-matching patterns to avoid infinite loops
        if (compiled.matcher('').find()) {
            System.err.println "Error: pattern '${options.p}' matches empty text."
            cli.usage()
            System.exit(1)
        }

        TextScan textScan = new TextScan(
            rootDir: options.d,
            pattern: options.p,
            newText: newText,
            replaceMode: replaceMode,
            // Literal mode: treat '$' and '\' in the new text as plain characters
            replacement: !replaceMode ? null : (asRegex ? newText : Matcher.quoteReplacement(newText)),
            regex: asRegex,
            reportFile: options.report ?: null,
            archiveDir: options.archive ?: null,
            compiledPattern: compiled
        )

        if (!textScan.run()) {
            cli.usage()
            System.exit(1)
        }
    }
}
