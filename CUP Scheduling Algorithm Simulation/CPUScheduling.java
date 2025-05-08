import java.util.*;

public class CPUScheduling {

    static class Process {
        int id;
        int arrivalTime;
        int burstTime;
        int remainingTime;
        int priority;

        // For metrics
        int startTime = -1;     
        int completionTime = 0; 
        int turnaroundTime = 0;
        int waitingTime = 0;

        Process(int id, int arrivalTime, int burstTime, int priority) {
            this.id = id;
            this.arrivalTime = arrivalTime;
            this.burstTime = burstTime;
            this.remainingTime = burstTime;
            this.priority = priority;
        }
    }

    // ----------------------------------------------------------------
    // Helper: Print the Gantt Chart from a list of "P0 " / "P1 " strings
    // ----------------------------------------------------------------
    static void printGanttChart(String algorithm, List<String> gantt) {
        System.out.println("\n=== " + algorithm + " Gantt Chart ===");
        for (String slot : gantt) {
            System.out.print(slot);
        }
        System.out.println("\n");
    }

    // ----------------------------------------------------------------
    // Helper: Once completionTime is known for each process, compute TAT & WT
    // ----------------------------------------------------------------
    static void calculateAndPrintResults(String algorithm, List<Process> processes) {
        int totalTAT = 0, totalWT = 0;
        System.out.println("===== " + algorithm + " Results =====");
        System.out.println("Process | Arrival | Burst | Priority | Start | Finish | Turnaround | Waiting");

        for (Process p : processes) {
            p.turnaroundTime = p.completionTime - p.arrivalTime;
            p.waitingTime    = p.turnaroundTime - p.burstTime;

            totalTAT += p.turnaroundTime;
            totalWT  += p.waitingTime;

            System.out.printf("P%-7d | %-7d | %-5d | %-8d | %-5d | %-6d | %-10d | %-6d\n",
                    p.id, p.arrivalTime, p.burstTime, p.priority,
                    p.startTime, p.completionTime, p.turnaroundTime, p.waitingTime);
        }

        double avgTAT = (double) totalTAT / processes.size();
        double avgWT  = (double) totalWT  / processes.size();

        System.out.println("\nTotal Turnaround Time: " + totalTAT);
        System.out.println("Average Turnaround Time: " + avgTAT);
        System.out.println("Total Waiting Time: " + totalWT);
        System.out.println("Average Waiting Time: " + avgWT);
        System.out.println("====================================\n");
    }

    // ========================================================================
    // 1. Round Robin (time-based) with a chosen quantum
    // ========================================================================
    static void roundRobin(List<Process> list, int quantum) {
        String algoName = "Round Robin (Q=" + quantum + ")";
        List<Process> processes = deepCopy(list);
        List<String> gantt = new ArrayList<>();

        // Sort by arrival time so we feed them into the ready queue in order
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        int currentTime = 0, index = 0;
        int finishedCount = 0;
        int n = processes.size();

        while (finishedCount < n) {
            // 1) Enqueue newly arrived
            while (index < n && processes.get(index).arrivalTime <= currentTime) {
                readyQueue.add(processes.get(index));
                index++;
            }

            // 2) If none ready, jump time to next arrival
            if (readyQueue.isEmpty()) {
                if (index < n) {
                    currentTime = processes.get(index).arrivalTime;
                }
            } else {
                // 3) Dequeue front
                Process current = readyQueue.poll();
                if (current.startTime < 0) {
                    current.startTime = currentTime;
                }

                // 4) Run it for time slice
                int slice = Math.min(quantum, current.remainingTime);
                current.remainingTime -= slice;

                for (int i = 0; i < slice; i++) {
                    gantt.add("P" + current.id + " ");
                }
                currentTime += slice;

                // 5) If done
                if (current.remainingTime == 0) {
                    current.completionTime = currentTime;
                    finishedCount++;
                } else {
                    // re-queue
                    // but first, enqueue any arrivals that came during the slice
                    while (index < n && processes.get(index).arrivalTime <= currentTime) {
                        readyQueue.add(processes.get(index));
                        index++;
                    }
                    readyQueue.add(current);
                }
            }
        }

        printGanttChart(algoName, gantt);
        calculateAndPrintResults(algoName, processes);
    }

    // ========================================================================
    // 2. Shortest Job Next (Non-preemptive SJF)
    // ========================================================================
    static void shortestJobNext(List<Process> list) {
        String algoName = "Shortest Job Next (Non-preemptive SJF)";
        List<Process> processes = deepCopy(list);
        List<String> gantt = new ArrayList<>();

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int currentTime = 0, finishedCount = 0;
        int n = processes.size();
        boolean[] done = new boolean[n];

        while (finishedCount < n) {
            // pick shortest burst among arrived & unfinished
            Process shortest = null;
            int shortestIndex = -1;

            for (int i = 0; i < n; i++) {
                Process p = processes.get(i);
                if (!done[i] && p.arrivalTime <= currentTime) {
                    if (shortest == null || p.burstTime < shortest.burstTime) {
                        shortest = p;
                        shortestIndex = i;
                    }
                }
            }

            // if none arrived yet, jump time
            if (shortest == null) {
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        nextArrival = Math.min(nextArrival, processes.get(i).arrivalTime);
                    }
                }
                currentTime = nextArrival;
                continue;
            }

            if (shortest.startTime < 0) {
                shortest.startTime = currentTime;
            }

            // run to completion
            for (int t = 0; t < shortest.burstTime; t++) {
                gantt.add("P" + shortest.id + " ");
            }
            currentTime += shortest.burstTime;
            shortest.remainingTime = 0;
            shortest.completionTime = currentTime;

            done[shortestIndex] = true;
            finishedCount++;
        }

        printGanttChart(algoName, gantt);
        calculateAndPrintResults(algoName, processes);
    }

    // ========================================================================
    // 3. Non-Preemptive Priority
    // ========================================================================
    static void nonPreemptivePriority(List<Process> list) {
        String algoName = "Non-Preemptive Priority";
        List<Process> processes = deepCopy(list);
        List<String> gantt = new ArrayList<>();

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int currentTime = 0, finishedCount = 0;
        int n = processes.size();
        boolean[] done = new boolean[n];

        while (finishedCount < n) {
            Process highest = null;
            int highestIndex = -1;

            for (int i = 0; i < n; i++) {
                Process p = processes.get(i);
                if (!done[i] && p.arrivalTime <= currentTime) {
                    if (highest == null) {
                        highest = p;
                        highestIndex = i;
                    } else {
                        // lower priority value => better
                        if (p.priority < highest.priority) {
                            highest = p;
                            highestIndex = i;
                        }
                    }
                }
            }

            // if none arrived, jump
            if (highest == null) {
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        nextArrival = Math.min(nextArrival, processes.get(i).arrivalTime);
                    }
                }
                currentTime = nextArrival;
                continue;
            }

            if (highest.startTime < 0) {
                highest.startTime = currentTime;
            }

            // run to completion
            for (int t = 0; t < highest.burstTime; t++) {
                gantt.add("P" + highest.id + " ");
            }
            currentTime += highest.burstTime;
            highest.remainingTime = 0;
            highest.completionTime = currentTime;

            done[highestIndex] = true;
            finishedCount++;
        }

        printGanttChart(algoName, gantt);
        calculateAndPrintResults(algoName, processes);
    }

    // ========================================================================
    // 4. Preemptive Priority (newly added)
    //    We pick, at each time unit, the highest priority (lowest priority value)
    //    among arrived processes. If none are ready, we jump time forward.
    //    We run for 1 time unit, then re-check (fully preemptive).
    // ========================================================================
    static void preemptivePriority(List<Process> list) {
        String algoName = "Preemptive Priority";
        List<Process> processes = deepCopy(list);
        List<String> gantt = new ArrayList<>();

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int currentTime = 0;
        int finishedCount = 0;
        int n = processes.size();
        boolean[] done = new boolean[n];

        while (finishedCount < n) {
            // pick the highest priority (lowest priority value) among arrived & unfinished
            Process current = null;
            int currentIndex = -1;

            for (int i = 0; i < n; i++) {
                Process p = processes.get(i);
                if (!done[i] && p.arrivalTime <= currentTime) {
                    if (current == null || p.priority < current.priority) {
                        current = p;
                        currentIndex = i;
                    }
                    // tie-break? up to you (e.g. earliest arrival, lowest ID, etc.)
                }
            }

            if (current == null) {
                // none arrived -> jump time
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        nextArrival = Math.min(nextArrival, processes.get(i).arrivalTime);
                    }
                }
                currentTime = nextArrival;
                continue;
            }

            // first time this process runs?
            if (current.startTime < 0) {
                current.startTime = currentTime;
            }

            // run it for 1 time unit (fully preemptive)
            gantt.add("P" + current.id + " ");
            current.remainingTime--;
            currentTime++;

            // if finished
            if (current.remainingTime == 0) {
                current.completionTime = currentTime;
                done[currentIndex] = true;
                finishedCount++;
            }
        }

        printGanttChart(algoName, gantt);
        calculateAndPrintResults(algoName, processes);
    }

    // ========================================================================
    // 5. Shortest Remaining Time (SRT) - Preemptive SJF
    // ========================================================================
    static void shortestRemainingTime(List<Process> list) {
        String algoName = "Shortest Remaining Time (SRT, Preemptive SJF)";
        List<Process> processes = deepCopy(list);
        List<String> gantt = new ArrayList<>();

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int currentTime = 0, finishedCount = 0;
        int n = processes.size();
        boolean[] done = new boolean[n];

        while (finishedCount < n) {
            // pick min remainingTime among arrived & unfinished
            Process current = null;
            int currentIndex = -1;
            for (int i = 0; i < n; i++) {
                Process p = processes.get(i);
                if (!done[i] && p.arrivalTime <= currentTime) {
                    if (current == null || p.remainingTime < current.remainingTime) {
                        current = p;
                        currentIndex = i;
                    }
                }
            }

            if (current == null) {
                int nextArrival = Integer.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (!done[i]) {
                        nextArrival = Math.min(nextArrival, processes.get(i).arrivalTime);
                    }
                }
                currentTime = nextArrival;
                continue;
            }

            if (current.startTime < 0) {
                current.startTime = currentTime;
            }

            // run 1 unit
            gantt.add("P" + current.id + " ");
            current.remainingTime--;
            currentTime++;

            // if finished
            if (current.remainingTime == 0) {
                current.completionTime = currentTime;
                done[currentIndex] = true;
                finishedCount++;
            }
        }

        printGanttChart(algoName, gantt);
        calculateAndPrintResults(algoName, processes);
    }

    // ----------------------------------------------------------------
    // Utility: Make a deep copy so each algorithm can run independently
    // ----------------------------------------------------------------
    static List<Process> deepCopy(List<Process> original) {
        List<Process> copy = new ArrayList<>();
        for (Process p : original) {
            copy.add(new Process(p.id, p.arrivalTime, p.burstTime, p.priority));
        }
        return copy;
    }

    // ----------------------------------------------------------------
    // Main
    // ----------------------------------------------------------------
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Example input:
        // n=4
        //   P0 -> "0,5,2"  => arrival=0, burst=5, priority=2
        //   P1 -> "1,3,1"
        //   P2 -> "2,8,3"
        //   P3 -> "3,6,1"
        // quantum=2
        // Then it will run:
        //   - Round Robin
        //   - Non-preemptive SJF (SJN)
        //   - Non-preemptive Priority
        //   - Preemptive Priority (NEW)
        //   - Shortest Remaining Time (SRT)

        System.out.print("Enter number of processes: ");
        int n = Integer.parseInt(sc.nextLine().trim());

        List<Process> processes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            System.out.print("Enter <arrival>,<burst>,<priority> for P" + i + ": ");
            String[] data = sc.nextLine().split(",");
            int arrival  = Integer.parseInt(data[0].trim());
            int burst    = Integer.parseInt(data[1].trim());
            int priority = Integer.parseInt(data[2].trim());
            processes.add(new Process(i, arrival, burst, priority));
        }

        System.out.print("Enter time quantum for Round Robin: ");
        int quantum = Integer.parseInt(sc.nextLine().trim());

        // 1) Round Robin
        roundRobin(processes, quantum);

        // 2) Shortest Job Next (non-preemptive)
        shortestJobNext(processes);

        // 3) Non-Preemptive Priority
        nonPreemptivePriority(processes);

        // 4) Preemptive Priority (NEW)
        preemptivePriority(processes);

        // 5) Shortest Remaining Time (SRT)
        shortestRemainingTime(processes);

        sc.close();
    }
}
