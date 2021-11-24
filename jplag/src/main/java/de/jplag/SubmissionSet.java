package de.jplag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import de.jplag.options.JPlagOptions;

/**
 * Collection of submissions and their basecode if it exists.
 */
public class SubmissionSet {
    /**
     * Submissions to check for plagiarism.
     */
    private List<Submission> submissions;

    /**
     * Base code submission if it exists.
     */
    private final Optional<Submission> baseCodeSubmission;

    private final ErrorCollector errorCollector;
    private final JPlagOptions options;

    private int errors = 0;
    private String currentSubmissionName;

    /**
     * @param submissions Submissions to check for plagiarism.
     * @param baseCode Base code submission if it exists.
     */
    public SubmissionSet(List<Submission> submissions, Optional<Submission> baseCode, ErrorCollector errorCollector, JPlagOptions options)
            throws ExitException {
        this.submissions = submissions;
        this.baseCodeSubmission = baseCode;
        this.errorCollector = errorCollector;
        this.options = options;
        parseAllSubmissions();
        filterValidSubmissions(); // TODO TS: Keep filtered Submissions
    }

    /**
     * @return Whether a basecode is available for this collection.
     */
    public boolean hasBaseCode() {
        return !baseCodeSubmission.isEmpty();
    }

    /**
     * Retrieve the base code of this collection.
     * @return The base code submission.
     * @note Asking for a non-existing basecode crashes the program.
     * @see #hasBaseCode
     */
    public Submission getBaseCode() {
        if (baseCodeSubmission.isEmpty()) {
            throw new AssertionError("Querying a non-existing basecode submission.");
        }
        return baseCodeSubmission.get();
    }

    /**
     * @return The number of submissions.
     */
    public int numberOfSubmissions() { // Ensure this is the right value (filtered vs. all)
        return submissions.size();
    }

    /**
     * Obtain the submissions.
     * @note Changes in the list are reflected in this instance.
     */
    public List<Submission> getSubmissions() { // TODO TS: Ensure this is the right value
        return submissions;
    }

    /**
     * Remove invalid submissions from the set.
     */
    private void filterValidSubmissions() {
        submissions = submissions.stream().filter(submission -> !submission.hasErrors()).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * TODO PB: Find a better way to separate parseSubmissions(...) and parseBaseCodeSubmission(...)
     */
    private void parseAllSubmissions() throws ExitException {
        try {
            parseSubmissions(submissions);
            if (baseCodeSubmission.isPresent()) {
                parseBaseCodeSubmission(baseCodeSubmission.get()); // cannot use ifPresent because of throws declaration
            }

        } catch (OutOfMemoryError e) {
            throw new ExitException("Out of memory during parsing of submission \"" + currentSubmissionName + "\"");
        } catch (Throwable e) {
            e.printStackTrace();
            throw new ExitException("Unknown exception during parsing of " + "submission \"" + currentSubmissionName + "\"");
        }
    }

    /**
     * Parse the given base code submission.
     */
    private void parseBaseCodeSubmission(Submission baseCode) throws ExitException {

        long startTime = System.currentTimeMillis();
        errorCollector.print("----- Parsing basecode submission: " + baseCode.getName() + "\n", null);

        if (!baseCode.parse()) {
            errorCollector.printErrors();
            throw new ExitException("Bad basecode submission");
        }

        if (baseCode.getTokenList() != null && baseCode.getNumberOfTokens() < options.getMinimumTokenMatch()) {
            throw new ExitException("Basecode submission contains fewer tokens than minimum match length allows!\n");
        }

        errorCollector.print("\nBasecode submission parsed!\n", null);

        long duration = System.currentTimeMillis() - startTime;
        errorCollector.print("\n", "\nTime for parsing Basecode: " + TimeUtil.formatDuration(duration) + "\n");

    }

    /**
     * Parse all given submissions.
     */
    private void parseSubmissions(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            System.out.println("No submissions to parse!");
            return;
        }

        int count = 0;

        long startTime = System.currentTimeMillis();
        Iterator<Submission> iter = submissions.iterator();

        int invalid = 0;
        while (iter.hasNext()) {
            boolean ok;
            boolean removed = false;
            Submission subm = iter.next();

            errorCollector.print(null, "------ Parsing submission: " + subm.getName() + "\n");
            currentSubmissionName = subm.getName();
            errorCollector.setCurrentSubmissionName(currentSubmissionName);

            if (!(ok = subm.parse())) {
                errors++;
            }

            count++;

            if (subm.getTokenList() != null && subm.getNumberOfTokens() < options.getMinimumTokenMatch()) {
                errorCollector.print(null, "Submission contains fewer tokens than minimum match length allows!\n");
                subm.setTokenList(null);
                invalid++;
                removed = true;
                iter.remove(); // TODO TS: Keep filtered Submissions
            }

            if (ok && !removed) {
                errorCollector.print(null, "OK\n");
            } else {
                errorCollector.print(null, "ERROR -> Submission removed\n");
            }
        }

        errorCollector.print(
                "\n" + (count - errors - invalid) + " submissions parsed successfully!\n" + errors + " parser error" + (errors != 1 ? "s!\n" : "!\n"),
                null);

        if (invalid != 0) {
            errorCollector.print(null,
                    invalid + ((invalid == 1) ? " submission is not valid because it contains" : " submissions are not valid because they contain")
                            + " fewer tokens than minimum match length allows.\n");
        }

        long duration = System.currentTimeMillis() - startTime;
        errorCollector.print("\n\n", "\nTotal time for parsing: " + TimeUtil.formatDuration(duration) + "\n" + "Time per parsed submission: "
                + (count > 0 ? (duration / count) : "n/a") + " msec\n\n");
    }

}