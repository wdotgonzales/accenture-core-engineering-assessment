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

class TextScan {
    static final String CHARSET = 'UTF-8'
    static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss.SSS')

    String rootDir
    String pattern
    boolean regex
    String reportFile
    Pattern compiledPattern
    int extraErrors = 0

    boolean run() {
        Instant start = Instant.now()

        if (!isDirValid(rootDir)) {
            System.err.println "Error: '${rootDir}' does not exist, is not a directory, or is not readable."
            return false
        }

        String outputProblem = validateOutputs()
        if (outputProblem) {
            System.err.println "Error: ${outputProblem}"
            return false
        }

        log('INFO', "Start: scanning ${rootDir} for \"${pattern}\" (${regex ? 'regex' : 'literal'} mode)")

        List results = scan()
        report(results)

        if (reportFile) { writeReport(results) }

        summary(results)
        log('INFO', "End: finished in ${formatDuration(Duration.between(start, Instant.now()))}")
        return true
    }

    static Pattern buildPattern(String text, boolean asRegex) {
        return asRegex ? Pattern.compile(text) : Pattern.compile(Pattern.quote(text))
    }

    boolean isDirValid(String path) {
        if (!path) { return false }
        def dir = new File(path)
        return dir.isDirectory() && dir.canRead()
    }

    String validateOutputs() {
        if (reportFile) {
            Path report = Paths.get(reportFile).toAbsolutePath().normalize()
            if (Files.isDirectory(report)) {
                return "--report '${reportFile}' is a folder; give a file path."
            }
            Path parent = report.parent
            if (Files.exists(parent) && !Files.isWritable(parent)) {
                return "cannot write the report into '${parent}'."
            }
        }
        return null
    }

    void writeReport(List results) {
        Path target = Paths.get(reportFile).toAbsolutePath().normalize()
        Path temp = null
        try {
            Files.createDirectories(target.parent)
            temp = Files.createTempFile(target.parent, '.textscan-', '.tmp')

            String nl = System.lineSeparator()
            temp.toFile().withWriter(CHARSET) { Writer w ->
                w << "TextScan report${nl}"
                w << "Folder: ${rootDir}${nl}"
                w << "Pattern: ${pattern} (${regex ? 'regex' : 'literal'})${nl}"
                w << "Generated: ${LocalDateTime.now().format(LOG_TIME)}${nl}${nl}"
                results.findAll { it.hits }.each { r ->
                    w << "${r.file}\t${r.hits} ${plural(r.hits, 'hit')}\t${lineLabel(r.lines)}${nl}"
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

    List scan() {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize()
        Path reportPath = reportFile ? Paths.get(reportFile).toAbsolutePath().normalize() : null

        List files = []

        root.toFile().traverse(
            type: FileType.FILES,
            nameFilter: ~/.*\.txt$/,
            preDir: { File dir ->
                Path dirPath = dir.toPath()
                if (dir.name.startsWith('.') || Files.isSymbolicLink(dirPath)) {
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
            Map result = [file: relPath.toString(), relPath: relPath, hits: 0, lines: [], error: null]

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

    void report(List results) {
        results.each { r ->
            if (r.error) {
                log('ERROR', "${r.file} -> could not read: ${r.error}")
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

    void summary(List results) {
        int scanned = results.size()
        int matched = results.count { it.hits }
        int totalHits = results.sum(0) { it.hits }
        int errors = results.count { it.error } + extraErrors

        log(errors ? 'WARN' : 'INFO',
            "Summary: ${scanned} ${plural(scanned, 'file')} scanned, ${matched} matched, " +
            "${totalHits} total ${plural(totalHits, 'hit')}, ${errors} ${plural(errors, 'error')}")
    }

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

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'groovy TextScan.groovy -d <directory> -p <pattern> [options]')
        cli.d(names: ['-d', '--dir'], args: 1, required: true, argName: 'directory', 'Folder to scan')
        cli.p(names: ['-p', '--pattern'], args: 1, required: true, argName: 'pattern', 'Text to search for')
        cli.r(names: ['-r', '--regex'], 'Treat the pattern as a regular expression')
        cli._(names: ['--report'], args: 1, argName: 'file', 'Write the list of matched files to this file')
        cli.h(names: ['-h', '--help'], usageHelp: true, 'Show this help and exit')

        def options = cli.parse(args)
        if (!options) { System.exit(1) }

        if (options.h) {
            cli.usage()
            System.exit(0)
        }

        Pattern compiled
        try {
            compiled = buildPattern(options.p, options.regex)
        } catch (PatternSyntaxException e) {
            System.err.println "Error: invalid regex '${options.p}': ${e.description}"
            cli.usage()
            System.exit(1)
        }

        if (compiled.matcher('').find()) {
            System.err.println "Error: pattern '${options.p}' matches empty text."
            cli.usage()
            System.exit(1)
        }

        TextScan textScan = new TextScan(
            rootDir: options.d,
            pattern: options.p,
            regex: options.regex ?: false,
            reportFile: options.report ?: null,
            compiledPattern: compiled
        )

        if (!textScan.run()) {
            cli.usage()
            System.exit(1)
        }
    }
}
