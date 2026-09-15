import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class CheckChangedCoverage {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);

        if (!Files.exists(config.coverageReport())) {
            fail("Coverage report not found: " + config.coverageReport());
        }

        Optional<String> compareRef = config.compareRef().isBlank()
                ? inferCompareRef()
                : Optional.of(config.compareRef());

        if (compareRef.isEmpty()) {
            System.out.println("Skipping changed-code coverage check: no comparison ref available.");
            return;
        }

        Optional<String> mergeBaseOutput = gitQuietly("merge-base", compareRef.get(), "HEAD");
        if (mergeBaseOutput.isEmpty() || mergeBaseOutput.get().isBlank()) {
            System.out.println("Skipping changed-code coverage check: no merge base between "
                    + compareRef.get() + " and HEAD.");
            return;
        }
        String mergeBase = mergeBaseOutput.get().trim();
        System.out.println("Comparing against " + compareRef.get() + " (merge base " + mergeBase + ").");

        Map<String, Set<Integer>> addedLines = readAddedLines(mergeBase, config.sourceRoots());
        if (addedLines.isEmpty()) {
            System.out.println("No changed Java source lines found under " + String.join(", ", config.sourceRoots()) + ".");
            return;
        }

        CoverageIndex coverageIndex = CoverageIndex.load(config.coverageReport(), config.sourceRoots());
        Evaluation evaluation = evaluate(addedLines, coverageIndex);

        if (!evaluation.missingCoverageFiles().isEmpty()) {
            fail("Changed Java files were not found in the JaCoCo report: "
                    + String.join(", ", evaluation.missingCoverageFiles()));
        }

        boolean failed = false;
        failed |= printAndCheck("line", evaluation.lineCovered(), evaluation.lineTotal(), config.threshold());
        failed |= printAndCheck("functionality", evaluation.methodCovered(), evaluation.methodTotal(), config.threshold());
        failed |= printAndCheck("decision", evaluation.branchCovered(), evaluation.branchTotal(), config.threshold());

        if (evaluation.lineTotal() == 0) {
            System.out.println("No executable changed Java lines found in the JaCoCo report.");
        }

        if (failed) {
            System.exit(1);
        }
    }

    private static Optional<String> inferCompareRef() {
        String mergeRequestTarget = getenv("CI_MERGE_REQUEST_TARGET_BRANCH_NAME");
        if (!mergeRequestTarget.isBlank()) {
            Optional<String> target = resolvable("origin/" + mergeRequestTarget);
            if (target.isPresent()) {
                return target;
            }
        }

        String beforeSha = getenv("CI_COMMIT_BEFORE_SHA");
        if (!beforeSha.isBlank() && !ZERO_SHA.equals(beforeSha)) {
            Optional<String> before = resolvable(beforeSha);
            if (before.isPresent()) {
                return before;
            }
        }

        String defaultBranch = getenv("CI_DEFAULT_BRANCH");
        String currentBranch = getenv("CI_COMMIT_BRANCH");
        if (!defaultBranch.isBlank() && !defaultBranch.equals(currentBranch)) {
            Optional<String> base = resolvable("origin/" + defaultBranch);
            if (base.isPresent()) {
                return base;
            }
        }

        return resolvable("HEAD~1");
    }

    /**
     * A candidate is only usable if this clone actually has it. A force-pushed
     * {@code CI_COMMIT_BEFORE_SHA}, or a branch whose fetch failed, otherwise reached
     * {@code git merge-base} and took the whole job down on a git error with no coverage
     * output at all -- a missing comparison point reported as a failed build.
     */
    private static Optional<String> resolvable(String ref) {
        return gitQuietly("rev-parse", "--verify", "--quiet", ref + "^{commit}").isPresent()
                ? Optional.of(ref)
                : Optional.empty();
    }

    private static Map<String, Set<Integer>> readAddedLines(String mergeBase, List<String> sourceRoots) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("diff");
        command.add("--unified=0");
        command.add("--diff-filter=ACMR");
        command.add("--no-ext-diff");
        command.add(mergeBase + "..HEAD");
        command.add("--");
        command.addAll(sourceRoots);

        String diff = git(command.toArray(String[]::new));
        Map<String, Set<Integer>> addedLines = new TreeMap<>();
        String currentPath = "";
        int currentNewLine = -1;

        try (BufferedReader reader = new BufferedReader(new StringReader(diff))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("+++ ")) {
                    currentPath = normalizeDiffPath(line.substring(4).trim());
                    if (!isJavaPathInRoots(currentPath, sourceRoots)) {
                        currentPath = "";
                    }
                    currentNewLine = -1;
                    continue;
                }

                if (currentPath.isBlank()) {
                    continue;
                }

                Matcher hunk = HUNK_HEADER.matcher(line);
                if (hunk.matches()) {
                    currentNewLine = Integer.parseInt(hunk.group(1));
                    continue;
                }

                if (currentNewLine < 0 || line.isEmpty()) {
                    continue;
                }

                char marker = line.charAt(0);
                if (marker == '+') {
                    addedLines.computeIfAbsent(currentPath, ignored -> new TreeSet<>()).add(currentNewLine);
                    currentNewLine++;
                } else if (marker == ' ') {
                    currentNewLine++;
                }
            }
        }

        return addedLines;
    }

    private static boolean isCoverageExcluded(String path) {
        return path.endsWith("/DemoApplication.java")
                || path.contains("/exception/");
    }

    private static Evaluation evaluate(
            Map<String, Set<Integer>> addedLines,
            CoverageIndex coverageIndex
    ) {
        Evaluation evaluation = new Evaluation();

        for (Map.Entry<String, Set<Integer>> entry : addedLines.entrySet()) {

            if (isCoverageExcluded(entry.getKey())) {
                System.out.println(
                        "Skipping coverage-excluded file: " + entry.getKey()
                );
                continue;
            }

            FileCoverage fileCoverage = coverageIndex.files().get(entry.getKey());

            if (fileCoverage == null) {
                evaluation.missingCoverageFiles().add(entry.getKey());
                continue;
            }

            Set<Integer> executableAddedLines = new HashSet<>();

            for (Integer lineNumber : entry.getValue()) {
                LineCoverage lineCoverage = fileCoverage.lines().get(lineNumber);

                if (lineCoverage == null || lineCoverage.instructionsTotal() == 0) {
                    continue;
                }

                executableAddedLines.add(lineNumber);
                evaluation.addLine(lineCoverage.coveredInstructions() > 0);
                evaluation.addBranches(
                        lineCoverage.coveredBranches(),
                        lineCoverage.missedBranches()
                );
            }

            for (MethodCoverage methodCoverage :
                    changedMethods(fileCoverage.methods(), executableAddedLines)) {
                evaluation.addMethod(methodCoverage.coveredMethods() > 0);
            }
        }

        return evaluation;
    }

    private static Set<MethodCoverage> changedMethods(List<MethodCoverage> methods, Set<Integer> executableAddedLines) {
        Set<MethodCoverage> changedMethods = new TreeSet<>(Comparator
                .comparingInt(MethodCoverage::line)
                .thenComparing(MethodCoverage::id));

        if (methods.isEmpty() || executableAddedLines.isEmpty()) {
            return changedMethods;
        }

        List<MethodCoverage> sortedMethods = new ArrayList<>(methods);
        sortedMethods.sort(Comparator.comparingInt(MethodCoverage::line).thenComparing(MethodCoverage::id));

        for (MethodCoverage method : sortedMethods) {
            int nextMethodLine = sortedMethods.stream()
                    .mapToInt(MethodCoverage::line)
                    .filter(line -> line > method.line())
                    .min()
                    .orElse(Integer.MAX_VALUE);
            int methodEndLine = nextMethodLine == Integer.MAX_VALUE ? Integer.MAX_VALUE : nextMethodLine - 1;

            boolean intersectsChangedLine = executableAddedLines.stream()
                    .anyMatch(line -> line >= method.line() && line <= methodEndLine);
            if (intersectsChangedLine) {
                changedMethods.add(method);
            }
        }

        return changedMethods;
    }

    private static boolean printAndCheck(String metric, int covered, int total, double threshold) {
        String label = "Changed code " + metric + " coverage";
        if (total == 0) {
            System.out.println(label + ": n/a (0 applicable items)");
            return false;
        }

        double percentage = covered * 100.0 / total;
        System.out.printf(Locale.ROOT, "%s: %.2f%% (%d/%d)%n", label, percentage, covered, total);

        if (percentage + 0.000001 < threshold) {
            System.err.printf(Locale.ROOT, "%s is below the %.2f%% threshold.%n", label, threshold);
            return true;
        }
        return false;
    }

    private static String git(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail("Git command failed: " + String.join(" ", command) + "\n" + output.trim());
        }
        return output;
    }



    /** Git that reports failure instead of ending the process. Empty when the command failed. */
    private static Optional<String> gitQuietly(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return process.waitFor() == 0 ? Optional.of(output) : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static String getenv(String name) {
        return System.getenv().getOrDefault(name, "").trim();
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private static String normalizeDiffPath(String path) {
        if (path.equals("/dev/null")) {
            return "";
        }
        if (path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    private static boolean isJavaPathInRoots(String path, List<String> sourceRoots) {
        return path.endsWith(".java") && sourceRoots.stream().anyMatch(root -> path.startsWith(root + "/"));
    }

    private static String sourcePath(String sourceRoot, String packageName, String sourceFileName) {
        String path = packageName.isBlank()
                ? sourceRoot + "/" + sourceFileName
                : sourceRoot + "/" + packageName + "/" + sourceFileName;
        return path.replace('\\', '/').replaceAll("/{2,}", "/");
    }

    private static int intAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private record Config(Path coverageReport, List<String> sourceRoots, String compareRef, double threshold) {
        private static Config parse(String[] args) {
            Path coverageReport = Path.of("target/site/jacoco/jacoco.xml");
            List<String> sourceRoots = new ArrayList<>(List.of("src/main/java"));
            String compareRef = "";
            double threshold = 30.0;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--coverage-report" -> coverageReport = Path.of(requiredValue(args, ++i, arg));
                    case "--source-root" -> sourceRoots.add(normalizeRoot(requiredValue(args, ++i, arg)));
                    case "--compare-ref" -> compareRef = requiredValue(args, ++i, arg);
                    case "--threshold" -> threshold = Double.parseDouble(requiredValue(args, ++i, arg));
                    case "--help" -> {
                        printUsage();
                        System.exit(0);
                    }
                    default -> fail("Unknown argument: " + arg);
                }
            }

            return new Config(coverageReport, sourceRoots.stream().map(Config::normalizeRoot).distinct().toList(), compareRef, threshold);
        }

        private static String requiredValue(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                fail("Missing value for " + option);
            }
            return args[index];
        }

        private static String normalizeRoot(String root) {
            return root.replace('\\', '/').replaceAll("^\\./", "").replaceAll("/+$", "");
        }

        private static void printUsage() {
            System.out.println("""
                    Usage: java scripts/CheckChangedCoverage.java [options]

                    Options:
                      --coverage-report PATH  JaCoCo XML report path. Defaults to target/site/jacoco/jacoco.xml.
                      --source-root PATH      Source root to evaluate. Defaults to src/main/java.
                      --compare-ref REF       Git ref to compare against. Defaults to GitLab CI merge target,
                                               CI_COMMIT_BEFORE_SHA, origin/default branch, or HEAD~1.
                      --threshold NUMBER      Minimum changed-code coverage percentage. Defaults to 30.
                    """);
        }
    }

    private record CoverageIndex(Map<String, FileCoverage> files) {
        private static CoverageIndex load(Path report, List<String> sourceRoots) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            configureSecureXmlParsing(factory);
            Document document = factory.newDocumentBuilder().parse(report.toFile());
            document.getDocumentElement().normalize();

            Map<String, FileCoverage> files = new LinkedHashMap<>();
            NodeList packageNodes = document.getDocumentElement().getElementsByTagName("package");
            for (int i = 0; i < packageNodes.getLength(); i++) {
                Element packageElement = (Element) packageNodes.item(i);
                String packageName = packageElement.getAttribute("name");

                Map<String, List<FileCoverage>> filesBySourceName = readSourceFiles(packageElement, packageName, sourceRoots, files);
                readMethods(packageElement, filesBySourceName);
            }

            return new CoverageIndex(files);
        }

        private static Map<String, List<FileCoverage>> readSourceFiles(
                Element packageElement,
                String packageName,
                List<String> sourceRoots,
                Map<String, FileCoverage> files
        ) {
            Map<String, List<FileCoverage>> filesBySourceName = new HashMap<>();
            NodeList children = packageElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element sourceFileElement) || !"sourcefile".equals(sourceFileElement.getTagName())) {
                    continue;
                }

                String sourceFileName = sourceFileElement.getAttribute("name");
                for (String sourceRoot : sourceRoots) {
                    String path = sourcePath(sourceRoot, packageName, sourceFileName);
                    FileCoverage fileCoverage = new FileCoverage(path);
                    NodeList lineNodes = sourceFileElement.getElementsByTagName("line");
                    for (int j = 0; j < lineNodes.getLength(); j++) {
                        Element lineElement = (Element) lineNodes.item(j);
                        int lineNumber = intAttribute(lineElement, "nr");
                        fileCoverage.lines().put(lineNumber, new LineCoverage(
                                intAttribute(lineElement, "mi"),
                                intAttribute(lineElement, "ci"),
                                intAttribute(lineElement, "mb"),
                                intAttribute(lineElement, "cb")
                        ));
                    }
                    files.put(path, fileCoverage);
                    filesBySourceName.computeIfAbsent(sourceFileName, ignored -> new ArrayList<>()).add(fileCoverage);
                }
            }
            return filesBySourceName;
        }

        private static void readMethods(Element packageElement, Map<String, List<FileCoverage>> filesBySourceName) {
            NodeList children = packageElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element classElement) || !"class".equals(classElement.getTagName())) {
                    continue;
                }

                String sourceFileName = classElement.getAttribute("sourcefilename");
                List<FileCoverage> sourceFiles = filesBySourceName.getOrDefault(sourceFileName, List.of());
                if (sourceFiles.isEmpty()) {
                    continue;
                }

                NodeList methodNodes = classElement.getElementsByTagName("method");
                for (int j = 0; j < methodNodes.getLength(); j++) {
                    Element methodElement = (Element) methodNodes.item(j);
                    int line = intAttribute(methodElement, "line");
                    if (line <= 0) {
                        continue;
                    }

                    Counter methodCounter = readCounter(methodElement, "METHOD");
                    MethodCoverage methodCoverage = new MethodCoverage(
                            classElement.getAttribute("name") + "#"
                                    + methodElement.getAttribute("name")
                                    + methodElement.getAttribute("desc")
                                    + "@" + line,
                            line,
                            methodCounter.covered()
                    );
                    sourceFiles.forEach(file -> file.methods().add(methodCoverage));
                }
            }
        }

        private static Counter readCounter(Element element, String type) {
            NodeList counterNodes = element.getElementsByTagName("counter");
            for (int i = 0; i < counterNodes.getLength(); i++) {
                Element counter = (Element) counterNodes.item(i);
                if (type.equals(counter.getAttribute("type"))) {
                    return new Counter(intAttribute(counter, "missed"), intAttribute(counter, "covered"));
                }
            }
            return new Counter(0, 0);
        }

        /**
         * Every JaCoCo report starts with {@code <!DOCTYPE report PUBLIC "-//JACOCO//DTD
         * Report 1.1//EN" "report.dtd">}, so banning doctype declarations outright made
         * this check abort with a parser error before it could read a single counter.
         * Refusing to load the external DTD and refusing to resolve entities closes the
         * XXE hole that the ban was there for, and tolerates the declaration itself.
         */
        private static void configureSecureXmlParsing(DocumentBuilderFactory factory) throws ParserConfigurationException {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        }
    }

    private record FileCoverage(String path, Map<Integer, LineCoverage> lines, List<MethodCoverage> methods) {
        private FileCoverage(String path) {
            this(path, new HashMap<>(), new ArrayList<>());
        }
    }

    private record LineCoverage(int missedInstructions, int coveredInstructions, int missedBranches, int coveredBranches) {
        private int instructionsTotal() {
            return missedInstructions + coveredInstructions;
        }
    }

    private record MethodCoverage(String id, int line, int coveredMethods) {
    }

    private record Counter(int missed, int covered) {
    }

    private static final class Evaluation {
        private int lineCovered;
        private int lineTotal;
        private int methodCovered;
        private int methodTotal;
        private int branchCovered;
        private int branchTotal;
        private final List<String> missingCoverageFiles = new ArrayList<>();

        private void addLine(boolean covered) {
            lineTotal++;
            if (covered) {
                lineCovered++;
            }
        }

        private void addMethod(boolean covered) {
            methodTotal++;
            if (covered) {
                methodCovered++;
            }
        }

        private void addBranches(int covered, int missed) {
            branchCovered += covered;
            branchTotal += covered + missed;
        }

        private int lineCovered() {
            return lineCovered;
        }

        private int lineTotal() {
            return lineTotal;
        }

        private int methodCovered() {
            return methodCovered;
        }

        private int methodTotal() {
            return methodTotal;
        }

        private int branchCovered() {
            return branchCovered;
        }

        private int branchTotal() {
            return branchTotal;
        }

        private List<String> missingCoverageFiles() {
            return missingCoverageFiles;
        }
    }
}
