package gitlabtools;

import static java.util.stream.Collectors.joining;

import java.io.PrintStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProgressTracker {

    private final PrintStream destination;
    private final int charsPerLine;

    private final Map<String, Character> progressChars = new HashMap<>();

    private final Map<String, Integer> progress = new LinkedHashMap<>();
    private final Map<String, Integer> info = new LinkedHashMap<>();
    private final StringWriter history = new StringWriter();
    private boolean interrupted = false;
    private boolean mute = false;

    /**
     * Creates a tracker with {@link System#in} as the destination and 50 chars per
     * line.
     */
    public ProgressTracker() {
        this(System.out, 50);
    }

    public ProgressTracker(PrintStream destination, int charsPerLine) {
        if (charsPerLine <= 0) {
            throw new IllegalArgumentException();
        }
        this.destination = destination;
        this.charsPerLine = charsPerLine;
        progress.put("successful", 0); // report successes first in summary
    }

    /**
     * Advances the progress by one step. A default result type
     * "<code>successful</code>" is used for this step.
     */
    public void advance() {
        advance("successful");
    }

    /**
     * Advances the progress by one step, using the given result type.
     */
    public void advance(String resultType) {
        if (interrupted) {
            if (!mute) {
                destination.print(history);
            }
            interrupted = false;
        }

        progress.merge(resultType, 1, Integer::sum);
        print(progressChars.getOrDefault(resultType, '.'));

        int totalProgress = totalProgress();
        if (totalProgress % charsPerLine == 0) {
            println(" (" + totalProgress + ")");
        }
    }

    /**
     * Registers an additional information, which is not counted as a progress
     * steps, but is reported in the summary in the end.
     */
    public void additionalInfo(String infoType) {
        info.merge(infoType, 1, Integer::sum);
    }

    /**
     * Interrupts this tracker, to allow for printing other messages to the
     * destination. The next time {@link #advance()} is called, all previous
     * progress output is printed again, to restore the visual indication of
     * progress.
     */
    public void interrupt() {
        interrupted = true;
        if (!mute) {
            destination.println();
        }
    }

    /**
     * Turns this tracker's progress reporting off. Progress is still tracked and
     * can be output using {@link #printSummary()}.
     */
    public void mute() {
        mute  = true;
    }

    /**
     * Prints a summary line to the destination.
     */
    public void printSummary() {
        if (totalProgress() % charsPerLine != 0) {
            destination.println();
        }
        destination.print("Done.");
        if (totalProgress() > 0) {
            destination.print(progress.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(joining(", ", " ", "")));
            if (!info.isEmpty()) {
                destination.print(info.entrySet().stream()
                        .map(e -> e.getValue() + " " + e.getKey())
                        .collect(joining(", ", " (", ")")));
            }
            destination.println(".");
        }
    }

    /**
     * Defines the character that is printed when a step of progress with the given
     * result type happens. The default character is the dot: '<code>.</code>'
     */
    public ProgressTracker usingChar(String resultType, char c) {
        progressChars.put(resultType, c);
        progress.put(resultType, 0); // influences order in which results are summarized
        return this;
    }

    private void println(String s) {
        if (!mute) {
            destination.println(s);
        }
        history.append(s).append("\n");
    }

    private void print(char c) {
        if (!mute) {
            destination.print(c);
        }
        history.append(c);
    }

    private int totalProgress() {
        return progress.values().stream().reduce(Integer::sum).get();
    }
}
