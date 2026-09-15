import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The bundle line-coverage gate, in two tiers.
 *
 * <p>Companion to {@link CheckChangedCoverage}, and deliberately a different question:
 * that one looks only at the lines a merge request touched, this one at the whole bundle.
 * Neither replaces the other -- a change can be fully covered and still drop the bundle,
 * and a bundle can sit comfortably above its floor while a new file arrives untested.
 *
 * <p>Two tiers because one number has to do two jobs that pull in opposite directions.
 * A floor low enough never to break somebody else's unrelated merge request is too low to
 * push the suite forward; a floor set at the current measurement breaks the build on the
 * first refactor that adds a line. So:
 *
 * <ul>
 *   <li><b>--min</b> is the floor. Below it the build fails. It is a regression guard, not
 *       an aspiration, and it should sit far enough below the measurement that ordinary
 *       work cannot trip it.</li>
 *   <li><b>--target</b> is where the suite is trying to be. Below it the script prints a
 *       warning and still exits 0, so the pipeline stays green -- unless
 *       <b>--fail-on-target</b> is passed, which is how a job marked
 *       {@code allow_failure: true} turns the same condition into a visible yellow
 *       warning in the pipeline instead of a line buried in a log.</li>
 * </ul>
 *
 * <p>Reads the JaCoCo XML report, which is written during the {@code test} phase, so it
 * does not need the build to have reached {@code verify}.
 *
 * <p>Usage:
 * <pre>
 *   java scripts/CheckLineCoverage.java --min 80 --target 90
 *   java scripts/CheckLineCoverage.java --min 80 --target 90 --fail-on-target
 *   java scripts/CheckLineCoverage.java --min 80 --target 90 --report path/to/jacoco.xml
 * </pre>
 */
public final class CheckLineCoverage {

    private static final String DEFAULT_REPORT = "target/site/jacoco/jacoco.xml";

    public static void main(String[] args) throws Exception {
        double min = 0;
        double target = 0;
        String report = DEFAULT_REPORT;
        boolean failOnTarget = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--min" -> min = requireNumber(args, ++i, "--min");
                case "--target" -> target = requireNumber(args, ++i, "--target");
                case "--report" -> report = requireValue(args, ++i, "--report");
                case "--fail-on-target" -> failOnTarget = true;
                default -> fail("Unknown argument: " + args[i]);
            }
        }

        if (target < min) {
            fail("--target (" + target + ") is below --min (" + min + "), which can never warn");
        }

        Path path = Path.of(report);
        if (!Files.exists(path)) {
            // A missing report is not "0% covered", it is a broken build. Saying so beats
            // failing the gate with a number nobody can act on.
            fail("Coverage report not found: " + path
                    + " -- has the test phase run? The report is written by jacoco:report.");
        }

        Counter lines = readBundleLineCounter(path);
        double percentage = lines.percentage();

        // Printed in the same shape CheckChangedCoverage uses, so a GitLab `coverage:`
        // regex can pick either up.
        System.out.printf("Bundle line coverage: %.2f%% (%d/%d lines)%n",
                percentage, lines.covered(), lines.total());
        System.out.printf("  floor  --min    %.2f%%%n", min);
        System.out.printf("  target --target %.2f%%%n", target);

        if (percentage < min) {
            System.out.printf("%nFAILED: line coverage %.2f%% is below the mandatory floor of %.2f%%.%n",
                    percentage, min);
            System.out.println("Add tests for what you changed, or argue the floor down deliberately.");
            System.exit(1);
        }

        if (percentage < target) {
            System.out.printf("%nWARNING: line coverage %.2f%% is below the %.2f%% target "
                    + "(floor of %.2f%% is met).%n", percentage, target, min);
            System.exit(failOnTarget ? 1 : 0);
        }

        System.out.printf("%nOK: line coverage %.2f%% meets the %.2f%% target.%n", percentage, target);
    }

    /**
     * The report-level LINE counter. Only direct children of {@code <report>} are read:
     * the same element name repeats on every package, class and method, and summing those
     * would count every line several times over.
     */
    private static Counter readBundleLineCounter(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // The JaCoCo report declares a DTD; resolving it would reach the network.
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document document = factory.newDocumentBuilder().parse(report.toFile());
        Element root = document.getDocumentElement();

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !"counter".equals(node.getNodeName())) {
                continue;
            }
            Element counter = (Element) node;
            if (!"LINE".equals(counter.getAttribute("type"))) {
                continue;
            }
            return new Counter(
                    Integer.parseInt(counter.getAttribute("missed")),
                    Integer.parseInt(counter.getAttribute("covered")));
        }

        fail("No report-level LINE counter in " + report + " -- is this a JaCoCo XML report?");
        return null;
    }

    private static double requireNumber(String[] args, int index, String name) {
        String raw = requireValue(args, index, name);
        try {
            double value = Double.parseDouble(raw);
            if (value < 0 || value > 100) {
                fail(name + " must be a percentage between 0 and 100, got: " + raw);
            }
            return value;
        } catch (NumberFormatException exception) {
            fail(name + " must be a number, got: " + raw);
            return 0;
        }
    }

    private static String requireValue(String[] args, int index, String name) {
        if (index >= args.length) {
            fail(name + " needs a value");
        }
        return args[index];
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private record Counter(int missed, int covered) {
        int total() {
            return missed + covered;
        }

        double percentage() {
            return total() == 0 ? 100.0 : 100.0 * covered / total();
        }
    }
}
