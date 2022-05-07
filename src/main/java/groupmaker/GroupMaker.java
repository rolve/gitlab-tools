package groupmaker;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static groupmaker.Pref.Strength.NONE;
import static java.lang.Integer.MAX_VALUE;
import static java.nio.file.Files.newBufferedWriter;
import static java.nio.file.Path.of;
import static java.util.Collections.shuffle;
import static java.util.Collections.sort;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static org.apache.commons.csv.CSVFormat.EXCEL;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.stream.Stream;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public class GroupMaker {

    static final Slot TUE = new Slot("tue", 13);
    static final Slot WED = new Slot("wed", 10);

    static final List<Slot> slots = List.of(TUE, WED);

    static final int TRIES = 10000;
    /**
     * Max size of allowed clusters. Reduce this number if the groups don't turn out
     * balanced enough.
     */
    static final int MAX_CLUSTER_SIZE = 30;

    public static void main(String[] rawArgs) throws Exception {
        var args = createCli(Args.class).parseArguments(rawArgs);
        var path = Path.of(args.getSurveyFile());

        var students = new CsvReader<>(EXCEL.withHeader(), Student.class).readAll(path);

        checkValidLegis(students);
        checkDuplicateLegis(students);

        Map<Slot, List<List<Student>>> best = null;
        int bestLargest = MAX_VALUE;
        int bestSmallest = 0;

        for (int i = 0; i < TRIES; i++) {
            shuffle(students);
            var slotGroups = new HashMap<Slot, List<List<Student>>>();

            /*
             * Assign students with slot preferences first (if this doesn't produce the
             * desired results, the code needs to be changed to take into account only
             * STRONG preferences :P )
             */
            for (var slot : slots) {
                var groups = new ArrayList<List<Student>>();
                range(0, slot.groups).forEach($ -> groups.add(new ArrayList<>()));
                slotGroups.put(slot, groups);

                var slotStudents = students.stream()
                        .filter(s -> s.pref().strength != NONE && s.pref().slot == slot);
                assignClusters(slotStudents, groups);
            }

            /*
             * Now assign students without slot preference. Try to put them together with
             * their magic number mates.
             */
            var rest = students.stream()
                    .filter(s -> s.pref().strength == NONE);
            var allGroups = slotGroups.values().stream()
                    .flatMap(List::stream).collect(toList());
            assignClusters(rest, allGroups);

            var stats = allGroups.stream().mapToInt(List::size).summaryStatistics();
            if (stats.getMax() < bestLargest ||
                    stats.getMax() == bestLargest && stats.getMin() > bestSmallest) {
                best = slotGroups;
                bestLargest = stats.getMax();
                bestSmallest = stats.getMin();
                System.out.println("Best so far (" + bestSmallest + "-" + bestLargest + "):");
                printGroups(best);
            }
        }
        
        for (var slot : slots) {
            System.out.println(slot.name);
            for (int i = 0; i < best.get(slot).size(); i++) {
                var group = best.get(slot).get(i);
                System.out.println("  Gruppe " + (i + 1) + " (" + group.size() + ")");
                sort(group, comparing(Student::pseudoMagicNumber));
                for (var student : group) {
                    System.out.printf("    %-11s ", student.niceLegi());
                    System.out.printf("[%8s] ", student.magicNumber);
                    System.out.println(student.name() + " (" + student.nethz + ")");
                }
                System.out.println();
            }
            System.out.println();
        }
        
        var writer = new PrintWriter(newBufferedWriter(of("groups.txt")));
        writer.println("Raumzeit,Nachname,Rufname,Nummer,NETHZ");
        for (var slot : slots) {
            var niceSlot = slot == TUE ? "Di" : "Mi";
            for (int i = 0; i < best.get(slot).size(); i++) {
                var group = best.get(slot).get(i);
                sort(group, comparing(Student::pseudoMagicNumber));
                for (var student : group) {
                    writer.print(niceSlot + " Gruppe " + (i+1) + ",");
                    writer.print(student.nachname + ",");
                    writer.print(student.vorname + ",");
                    writer.print(student.niceLegi() + ",");
                    writer.println(student.nethz);
                }
            }
        }
        writer.close();
    }

    private static void assignClusters(Stream<Student> students,
            List<List<Student>> groups) {
        boolean matchExisting = !groups.stream().allMatch(List::isEmpty);

        var clusters = new PriorityQueue<List<Student>>(comparing(List::size, reverseOrder()));
        clusters.addAll(students
                .collect(groupingBy(Student::pseudoMagicNumber)).values());
        var groupQueue = new PriorityQueue<List<Student>>(comparing(List::size));
        groupQueue.addAll(groups);

        while (!clusters.isEmpty()) {
            var largestCluster = clusters.remove();

            List<Student> smallestGroup = null;
            // First, try to find a group with members with this magic number...
            if (matchExisting && largestCluster.get(0).hasMagicNumber()) {
                var magic = largestCluster.get(0).magicNumber;
                smallestGroup = groupQueue.stream()
                        .filter(g -> g.stream().anyMatch(s -> s.magicNumber.equals(magic)))
                        .findFirst().orElse(null);
                groupQueue.remove(smallestGroup);
            }
            // ... else, just take the smallest group
            if (smallestGroup == null) {
                smallestGroup = groupQueue.remove();
            }

            int clusterSize = largestCluster.size();
            if (clusterSize > MAX_CLUSTER_SIZE) {
                System.err.println("Splitting cluster with magic number "
                        + largestCluster.get(0).magicNumber);
                clusters.add(largestCluster.subList(0, clusterSize / 2));
                clusters.add(largestCluster.subList(clusterSize / 2, clusterSize));
            } else {
                smallestGroup.addAll(largestCluster);
                groupQueue.add(smallestGroup);
            }
        }
    }

    private static void printGroups(Map<Slot, List<List<Student>>> groups) {
        for (var slot : slots) {
            System.out.println(slot);
            for (var group : groups.get(slot)) {
                System.out.println(group.size());
            }
            System.out.println();
        }
    }

    private static void checkValidLegis(List<Student> students) {
        students.stream()
                .filter(s -> !s.niceLegi().matches("\\d{2}-\\d{3}-\\d{3}"))
                .forEach(s -> System.err.println(s.legi + " (" + s.name() + ")"));
    }

    private static void checkDuplicateLegis(List<Student> students) {
        var dups = students.stream()
                .collect(groupingBy(Student::niceLegi))
                .entrySet().stream()
                .map(Entry::getValue)
                .filter(l -> l.size() > 1)
                .collect(toList());
        if (!dups.isEmpty()) {
            System.err.println("Legi duplicates:");
            for (var list : dups) {
                System.err.print(list.get(0).niceLegi() + ": ");
                System.err.println(list.stream().map(Student::name).collect(joining(", ")));
            }
        }
    }

    public interface Args {
        @Option(defaultValue = "UserResponses.csv")
        String getSurveyFile();
    }
}
