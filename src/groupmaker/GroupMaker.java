package groupmaker;

import static com.lexicalscope.jewel.cli.CliFactory.createCli;
import static groupmaker.Pref.Strength.NONE;
import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.*;
import static java.util.stream.IntStream.range;
import static org.apache.commons.csv.CSVFormat.EXCEL;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import com.lexicalscope.jewel.cli.Option;

import csv.CsvReader;

public class GroupMaker {

    static final Slot TUE = new Slot("tue", 13);
    static final Slot WED = new Slot("wed", 10);
    static final int MAX_SIZE = 24;

    static final List<Slot> slots = List.of(TUE, WED);

    public static void main(String[] rawArgs) throws Exception {
        var args = createCli(Args.class).parseArguments(rawArgs);
        var path = Path.of(args.getSurveyFile());

        var students = new CsvReader(EXCEL.withHeader()).read(path, Student.class);

        checkValidLegis(students);
        checkDuplicateLegis(students);

        var slotGroups = new HashMap<Slot, List<List<Student>>>();

        /*
         * Assign students with slot preferences first (if this is impossible, the code
         * needs to be changed to take into account only STRONG preferences :P )
         */
        for (var slot : slots) {
            var groups = new ArrayList<List<Student>>();
            range(0, slot.groups).forEach($ -> groups.add(new ArrayList<>()));
            slotGroups.put(slot, groups);

            var slotStudents = students.stream()
                    .filter(s -> s.pref().strength != NONE && s.pref().slot == slot)
                    .collect(toList());
            if (slotStudents.size() > slot.groups * MAX_SIZE) {
                System.err.println("Too many students (" + slotStudents.size() + ") for " + slot);
                System.exit(1);
            }

            assignClusters(slotStudents, groups);
        }
        printGroups(slotGroups, "After slot preferences");

        /*
         * Now assign students without slot preference. Try to put them together with
         * their magic number mates.
         */
        var rest = students.stream()
                .filter(s -> s.pref().strength == NONE)
                .collect(toList());
        var allGroups = slotGroups.values().stream()
                .flatMap(List::stream).collect(toList());
        assignClusters(rest, allGroups);
        printGroups(slotGroups, "Finished");
    }

    private static void assignClusters(List<Student> students,
            List<List<Student>> groups) {
        boolean matchExisting = !groups.stream().allMatch(List::isEmpty);

        var clusters = new PriorityQueue<List<Student>>(comparing(List::size, reverseOrder()));
        clusters.addAll(students.stream()
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

            var clusterSize = largestCluster.size();
            // If we can fit the entire cluster in the smallest (matching) group,
            // or if it's just one person...
            if (smallestGroup.size() + clusterSize <= MAX_SIZE || clusterSize == 1) {
                // ...add them
                smallestGroup.addAll(largestCluster);
                groupQueue.add(smallestGroup);
            } else if (largestCluster.size() >= 4) {
                // else, if the cluster is relatively large, split it
                System.err.println("Need to split cluster with magic number "
                        + largestCluster.get(0).magicNumber);
                clusters.add(largestCluster.subList(0, clusterSize / 2));
                clusters.add(largestCluster.subList(clusterSize / 2, clusterSize));
            } else {
                // else, just add it to the smallest (not necessarily matching) group
                System.err.println("Couldn't put " + clusterSize + " folks with magic number "
                        + largestCluster.get(0).magicNumber + " together with others");
                groupQueue.add(smallestGroup);
                smallestGroup = groupQueue.remove();
                smallestGroup.addAll(largestCluster);
                groupQueue.add(smallestGroup);
            }
        }
    }

    private static void printGroups(Map<Slot, List<List<Student>>> groups, String msg) {
        System.out.println(msg + ":");
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
