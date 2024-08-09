package ch.trick17.gitlabtools;

public class ProgressTrackerTest {
    public static void main(String[] args) throws InterruptedException {
        var tracker = new ProgressTracker()
                .usingChar("existing", '-').usingChar("failed", 'X');

        for (int i = 0; i < 320; i++) {
            double random = Math.random();
            if (random < 0.01) {
                tracker.interrupt();
                System.out.println("Error!");
                tracker.advance("failed");
            } else if (random < 0.2) {
                tracker.advance("existing");
            } else {
                tracker.advance();
            }

            if (random > 0.9) {
                tracker.additionalInfo("newly cloned");
            }

            Thread.sleep((long) (50 + Math.random() * 50));
        }

        tracker.printSummary();
    }
}
