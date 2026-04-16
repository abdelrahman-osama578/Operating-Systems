package os_ms1;

import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

public class OperatingSystem {
    int clock = 0;
    Memory memory = new Memory();
    
    Queue<PCB> readyQueue = new LinkedList<>(); 
    Queue<PCB>[] mlfq = new LinkedList[4]; 
    List<PCB> allProcesses = new ArrayList<>();
    
    Mutex userInput = new Mutex("userInput");
    Mutex userOutput = new Mutex("userOutput");
    Mutex fileAccess = new Mutex("fileAccess");

    boolean p1Arrived = false, p2Arrived = false, p3Arrived = false;
    
    int timeSlice; 
    int arrivalTimeP1;
    int arrivalTimeP2;
    int arrivalTimeP3;

    OSDashboard gui;
    String activeScheduler; 
    String currentInstructionDisplay = "None"; 
    int currentExecutingAddress = -1; 
    
    public Semaphore stepSemaphore = new Semaphore(0);
    public boolean isAutoRun = false;
    public volatile boolean isRunning = true; 
    
    public static class GanttRecord {
        public int pid, start, end;
        public GanttRecord(int pid, int start, int end) { this.pid = pid; this.start = start; this.end = end; }
    }
    public List<GanttRecord> timeline = new ArrayList<>();

    public OperatingSystem(OSDashboard gui, String schedulerType, int ts, int arr1, int arr2, int arr3) {
        this.gui = gui;
        this.activeScheduler = schedulerType;
        this.timeSlice = ts;
        this.arrivalTimeP1 = arr1;
        this.arrivalTimeP2 = arr2;
        this.arrivalTimeP3 = arr3;
        for(int i=0; i<4; i++) mlfq[i] = new LinkedList<>(); 
    }

    public void stepCycle() { stepSemaphore.release(); }
    public void toggleAutoRun() { isAutoRun = !isAutoRun; if (isAutoRun) stepSemaphore.release(); }
    private void pauseExecution() {
        if (!isRunning) return; 
        if (!isAutoRun) { try { stepSemaphore.acquire(); } catch (InterruptedException e) { }
        } else { try { Thread.sleep(1200); } catch (InterruptedException e) { } }
    }
    public void shutdown() { isRunning = false; stepSemaphore.release(); }

    public void runSchedulerRR() {
        System.out.println("OS Booted (Round Robin). Waiting for first cycle...");
        printSystemState(null); pauseExecution();       

        while (isRunning && hasUnfinishedProcesses()) {
            checkForNewArrivals(clock);

            if (readyQueue.isEmpty()) {
                recordIdleTime();
                clock++; currentInstructionDisplay = "None"; printSystemState(null); pauseExecution(); continue;
            }

            PCB currentProcess = readyQueue.poll();
            currentProcess.state = State.RUNNING;
            if (currentProcess.startTime == -1) currentProcess.startTime = clock;
            
            ensureInMemory(currentProcess); 

            int startCycle = clock;
            int instructionsExecuted = 0;
            
            while (isRunning && instructionsExecuted < timeSlice && currentProcess.state == State.RUNNING) {
                boolean executed = executeInstruction(currentProcess);
                
                // --- THE FIX: INSTANTLY FREE MEMORY WHEN FINISHED ---
                if (!executed && currentProcess.state == State.FINISHED) {
                    currentProcess.finishTime = clock; 
                    freeMemory(currentProcess);
                    break;
                }
                
                currentProcess.cpuCycles++;
                instructionsExecuted++; 
                clock++; 
                checkForNewArrivals(clock);
                
                printSystemState(currentProcess); pauseExecution(); 
            }
            
            recordGantt(currentProcess.processID, startCycle, clock);

            if (isRunning && currentProcess.state == State.RUNNING) {
                currentProcess.state = State.READY;
                readyQueue.add(currentProcess);
            }
        }
        finishOS();
    }

    public void runSchedulerHRRN() {
        System.out.println("OS Booted (HRRN). Waiting for first cycle...");
        printSystemState(null); pauseExecution();

        while (isRunning && hasUnfinishedProcesses()) {
            checkForNewArrivals(clock);

            if (readyQueue.isEmpty()) {
                recordIdleTime();
                clock++; currentInstructionDisplay = "None"; printSystemState(null); pauseExecution(); continue;
            }

            PCB currentProcess = null;
            double maxRatio = -1;
            for (PCB p : readyQueue) {
                int waitingTime = (clock - p.arrivalTime) - p.cpuCycles; 
                int expectedBurst = p.burstTime; 
                double ratio = (waitingTime + expectedBurst) / (double) expectedBurst;
                if (ratio > maxRatio) { maxRatio = ratio; currentProcess = p; }
            }
            readyQueue.remove(currentProcess); 
            currentProcess.state = State.RUNNING;
            if (currentProcess.startTime == -1) currentProcess.startTime = clock;
            
            ensureInMemory(currentProcess); 

            int startCycle = clock;
            while (isRunning && currentProcess.state == State.RUNNING) {
                boolean executed = executeInstruction(currentProcess);
                
                // --- THE FIX: INSTANTLY FREE MEMORY WHEN FINISHED ---
                if (!executed && currentProcess.state == State.FINISHED) {
                    currentProcess.finishTime = clock;
                    freeMemory(currentProcess);
                    break;
                }
                
                currentProcess.cpuCycles++;
                clock++; 
                checkForNewArrivals(clock);
                
                printSystemState(currentProcess); pauseExecution();
            }
            recordGantt(currentProcess.processID, startCycle, clock);
        }
        finishOS();
    }

    public void runSchedulerMLFQ() {
        System.out.println("OS Booted (MLFQ). Waiting for first cycle...");
        printSystemState(null); pauseExecution();

        while (isRunning && hasUnfinishedProcesses()) {
            checkForNewArrivals(clock);

            PCB currentProcess = getHighestPriorityProcessMLFQ();

            if (currentProcess == null) {
                recordIdleTime();
                clock++; currentInstructionDisplay = "None"; printSystemState(null); pauseExecution(); continue;
            }

            currentProcess.state = State.RUNNING;
            if (currentProcess.startTime == -1) currentProcess.startTime = clock;
            
            ensureInMemory(currentProcess); 

            int startCycle = clock;
            int currentQuantum = (int) Math.pow(2, currentProcess.priorityLevel);
            if (currentProcess.priorityLevel == 3) currentQuantum = timeSlice; 

            boolean preemptedByHigher = false;
            while (isRunning && currentProcess.quantumUsed < currentQuantum && currentProcess.state == State.RUNNING) {
                boolean executed = executeInstruction(currentProcess);
                
                // --- THE FIX: INSTANTLY FREE MEMORY WHEN FINISHED ---
                if (!executed && currentProcess.state == State.FINISHED) {
                    currentProcess.finishTime = clock;
                    freeMemory(currentProcess);
                    break;
                }
                
                currentProcess.cpuCycles++;
                currentProcess.quantumUsed++; 
                clock++; 
                checkForNewArrivals(clock);
                
                if (currentProcess.priorityLevel > 0) {
                    for (int i = 0; i < currentProcess.priorityLevel; i++) {
                        if (!mlfq[i].isEmpty()) {
                            preemptedByHigher = true;
                            break;
                        }
                    }
                }
                
                printSystemState(currentProcess); pauseExecution();
                if (preemptedByHigher) break; 
            }
            
            recordGantt(currentProcess.processID, startCycle, clock);

            if (isRunning && currentProcess.state == State.RUNNING) {
                currentProcess.state = State.READY;
                if (currentProcess.quantumUsed >= currentQuantum) {
                    currentProcess.quantumUsed = 0; 
                    if (currentProcess.priorityLevel < 3) currentProcess.priorityLevel++; 
                }
                addToReadyQueue(currentProcess);
            } else if (currentProcess.state == State.BLOCKED) {
                currentProcess.quantumUsed = 0; 
            }
        }
        finishOS();
    }

    // ==========================================
    // GARBAGE COLLECTION & SWAPPING ENGINE
    // ==========================================
    
    // NEW: Deletes "Zombie" processes to prevent memory leaks
    private void freeMemory(PCB p) {
        if (p.minMemoryBoundary != -1) {
            for (int i = p.minMemoryBoundary; i <= p.maxMemoryBoundary; i++) {
                memory.write(i, null);
            }
            if (gui != null) gui.appendLog("\n>> [MEMORY FREED] Process " + p.processID + " terminated successfully. Memory reclaimed.\n");
            p.minMemoryBoundary = -1;
            p.maxMemoryBoundary = -1;
        }
    }
    
    private void ensureInMemory(PCB p) {
        if (p.minMemoryBoundary != -1) return; 

        int requiredSpace = p.burstTime + 3;
        int loc = allocateMemorySpace(requiredSpace);
        
        while (loc == -1) {
            PCB victim = null;
            // 1. Try to swap out a blocked process first
            for (PCB other : allProcesses) { if (other.processID != p.processID && other.minMemoryBoundary != -1 && other.state == State.BLOCKED) { victim = other; break; } }
            // 2. Try to swap out a ready process
            if (victim == null) { for (PCB other : allProcesses) { if (other.processID != p.processID && other.minMemoryBoundary != -1 && other.state == State.READY) { victim = other; break; } } }
            // 3. Fallback: Evict finished zombies just in case
            if (victim == null) { for (PCB other : allProcesses) { if (other.processID != p.processID && other.minMemoryBoundary != -1 && other.state == State.FINISHED) { victim = other; break; } } }

            if (victim != null) {
                memory.swapToDisk(victim);
                if(gui != null) {
                    String msg = "MEMORY FULL! PROCESS ID: [" + victim.processID + "] is being swapped OUT to Disk.";
                    gui.appendLog(">> " + msg);
                    try { javax.swing.SwingUtilities.invokeAndWait(() -> gui.showSwapNotification(msg, "CRITICAL: SWAP OUT")); } catch (Exception e) {}
                }
                victim.minMemoryBoundary = -1; victim.maxMemoryBoundary = -1;
                loc = allocateMemorySpace(requiredSpace);
            } else { break; } // Catastrophic memory failure (Process requires more than 40 words total)
        }

        if (loc != -1) {
            memory.swapFromDisk(p, loc);
            if(gui != null) {
                String msg = "PROCESS ID: [" + p.processID + "] is being swapped IN to Main Memory at Address 0x" + String.format("%04X", loc);
                gui.appendLog(">> " + msg);
                try { javax.swing.SwingUtilities.invokeAndWait(() -> gui.showSwapNotification(msg, "CRITICAL: SWAP IN")); } catch (Exception e) {}
            }
        } 
    }

    private void recordGantt(int pid, int start, int end) {
        if (end > start) {
            if (!timeline.isEmpty() && timeline.get(timeline.size()-1).pid == pid && timeline.get(timeline.size()-1).end == start) {
                timeline.get(timeline.size()-1).end = end; 
            } else { timeline.add(new GanttRecord(pid, start, end)); }
        }
    }
    
    private void recordIdleTime() {
        if (!timeline.isEmpty() && timeline.get(timeline.size()-1).pid == 0 && timeline.get(timeline.size()-1).end == clock) {
            timeline.get(timeline.size()-1).end = clock + 1; 
        } else { timeline.add(new GanttRecord(0, clock, clock + 1)); }
    }

    private void finishOS() {
        if (!isRunning) return;
        if(gui != null) {
            currentInstructionDisplay = "SYSTEM HALTED";
            gui.updateDashboard(clock, "SYSTEM HALTED", currentInstructionDisplay, -1, getMemoryArray(), "\n>>> ALL PROCESSES FINISHED. SYSTEM HALTED.");
            gui.showMetricsReport(activeScheduler, timeline, allProcesses);
        }
    }

    private PCB getHighestPriorityProcessMLFQ() {
        for (int i = 0; i < 4; i++) if (!mlfq[i].isEmpty()) return mlfq[i].poll();
        return null;
    }

    private void addToReadyQueue(PCB pcb) {
        if (activeScheduler.equals("MLFQ")) mlfq[pcb.priorityLevel].add(pcb);
        else readyQueue.add(pcb);
    }
    
    private boolean hasUnfinishedProcesses() {
        if (allProcesses.isEmpty()) return true; 
        for (PCB p : allProcesses) {
            if (p.state != State.FINISHED) return true; 
        }
        return false; 
    }
    
    private boolean executeInstruction(PCB pcb) {
        int instructionAddress = pcb.minMemoryBoundary + pcb.programCounter;
        currentExecutingAddress = instructionAddress; 
        
        String instruction = memory.read(instructionAddress);
        
        if (instruction == null || instruction.equals("EOF") || instruction.startsWith("VAR_")) {
            pcb.state = State.FINISHED;
            return false; 
        }

        currentInstructionDisplay = instruction; 

        String[] tokens = instruction.split(" ");
        String command = tokens[0];

        switch (command) {
            case "print": 
                String pOut = "\n==========\n>> [PROCESS " + pcb.processID + "] PRINT OUT: " + getVariable(pcb, tokens[1]) + "\n==========";
                if(gui != null) gui.appendLog(pOut);
                break;
            case "assign":
                if (tokens.length == 3 && tokens[2].equals("input")) {
                    final String[] result = new String[1];
                    try { javax.swing.SwingUtilities.invokeAndWait(() -> { result[0] = gui.requestInput(pcb.processID, tokens[1]); });
                    } catch (Exception e) { e.printStackTrace(); }
                    setVariable(pcb, tokens[1], result[0]);
                } else if (tokens.length == 4 && tokens[2].equals("readFile")) {
                    setVariable(pcb, tokens[1], actualReadFile(getVariable(pcb, tokens[3]), pcb.processID));
                } else { setVariable(pcb, tokens[1], getVariable(pcb, tokens[2])); }
                break;
            case "printFromTo":
                int start = Integer.parseInt(getVariable(pcb, tokens[1])); int end = Integer.parseInt(getVariable(pcb, tokens[2]));
                StringBuilder sb = new StringBuilder("\n==========\n>> [PROCESS " + pcb.processID + "] PRINT SEQUENCE: ");
                for (int i = start; i <= end; i++) sb.append(i).append(" "); sb.append("\n==========");
                if(gui != null) gui.appendLog(sb.toString());
                break;
            case "writeFile": actualWriteFile(getVariable(pcb, tokens[1]), getVariable(pcb, tokens[2]), pcb.processID); break;
            case "semWait": handleSemWait(pcb, tokens[1]); break;
            case "semSignal": handleSemSignal(pcb, tokens[1]); break;
        }
        pcb.programCounter++; 
        return true;
    }

    private void handleSemWait(PCB pcb, String resource) {
        boolean acquired = false;
        if (resource.equals("userInput")) acquired = userInput.semWait(pcb);
        if (resource.equals("userOutput")) acquired = userOutput.semWait(pcb);
        if (resource.equals("file")) acquired = fileAccess.semWait(pcb);
    }

    private void handleSemSignal(PCB pcb, String resource) {
        PCB unblocked = null;
        if (resource.equals("userInput")) unblocked = userInput.semSignal(pcb);
        if (resource.equals("userOutput")) unblocked = userOutput.semSignal(pcb);
        if (resource.equals("file")) unblocked = fileAccess.semSignal(pcb);

        if (unblocked != null) addToReadyQueue(unblocked); 
    }

    private void setVariable(PCB pcb, String varName, String value) {
        for (int i = pcb.maxMemoryBoundary - 2; i <= pcb.maxMemoryBoundary; i++) {
            String currentMem = memory.read(i);
            if (currentMem == null || currentMem.startsWith("VAR_" + varName + "=") || currentMem.equals("EMPTY_VAR")) {
                memory.write(i, "VAR_" + varName + "=" + value); return;
            }
        }
    }

    private String getVariable(PCB pcb, String varName) {
        for (int i = pcb.maxMemoryBoundary - 2; i <= pcb.maxMemoryBoundary; i++) {
            String currentMem = memory.read(i);
            if (currentMem != null && currentMem.startsWith("VAR_" + varName + "=")) return currentMem.split("=")[1];
        }
        return varName; 
    }
    
    private String actualReadFile(String filename, int processId) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) content.append(line).append(" ");
            if(gui != null) gui.appendLog("\n>> [PROCESS " + processId + "] DISK I/O: Read data from " + filename);
            return content.toString().trim();
        } catch (IOException e) { 
            if(gui != null) gui.appendLog("\n>> [PROCESS " + processId + "] DISK ERROR: " + filename + " not found!"); return "File_Not_Found"; 
        }
    }

    private void actualWriteFile(String filename, String data, int processId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(data);
            if(gui != null) gui.appendLog("\n>> [PROCESS " + processId + "] DISK I/O: Successfully wrote to " + filename);
        } catch (IOException e) { }
    }
    
    private void checkForNewArrivals(int time) {
        if (time == arrivalTimeP1 && !p1Arrived) { loadProcessToMemory("Program_1.txt", 1); p1Arrived = true; }
        if (time == arrivalTimeP2 && !p2Arrived) { loadProcessToMemory("Program_2.txt", 2); p2Arrived = true; }
        if (time == arrivalTimeP3 && !p3Arrived) { loadProcessToMemory("Program_3.txt", 3); p3Arrived = true; }
    }

    private void loadProcessToMemory(String filename, int pid) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            List<String> instructions = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) if (!line.trim().isEmpty()) instructions.add(line.trim());
            
            int requiredSpace = instructions.size() + 3; 
            int minBoundary = allocateMemorySpace(requiredSpace);
            
            while (minBoundary == -1) {
                PCB victim = null;
                for (PCB other : allProcesses) { if (other.minMemoryBoundary != -1 && other.state == State.BLOCKED) { victim = other; break; } }
                if (victim == null) { for (PCB other : allProcesses) { if (other.minMemoryBoundary != -1 && other.state == State.READY) { victim = other; break; } } }
                if (victim == null) { for (PCB other : allProcesses) { if (other.minMemoryBoundary != -1 && other.state == State.FINISHED) { victim = other; break; } } }
                
                if (victim != null) {
                    memory.swapToDisk(victim);
                    if(gui != null) {
                        String msg = "MEMORY FULL! PROCESS ID: [" + victim.processID + "] is being swapped OUT to Disk.";
                        gui.appendLog(">> " + msg);
                        try { javax.swing.SwingUtilities.invokeAndWait(() -> gui.showSwapNotification(msg, "CRITICAL: SWAP OUT")); } catch (Exception e) {}
                    }
                    victim.minMemoryBoundary = -1; victim.maxMemoryBoundary = -1;
                    minBoundary = allocateMemorySpace(requiredSpace);
                } else { break; }
            }

            if (minBoundary != -1) {
                for (int i = 0; i < instructions.size(); i++) memory.write(minBoundary + i, instructions.get(i));
                for (int i = (minBoundary + requiredSpace - 1) - 2; i <= (minBoundary + requiredSpace - 1); i++) memory.write(i, "EMPTY_VAR");

                PCB newPcb = new PCB(pid, minBoundary, (minBoundary + requiredSpace - 1), clock, instructions.size());
                allProcesses.add(newPcb);
                addToReadyQueue(newPcb);
            } 
        } catch (IOException e) { }
    }

    private int allocateMemorySpace(int requiredSpace) {
        int consecutiveFree = 0, startIndex = -1;
        for (int i = 0; i < 40; i++) {
            if (memory.read(i) == null) {
                if (consecutiveFree == 0) startIndex = i;
                consecutiveFree++;
                if (consecutiveFree == requiredSpace) return startIndex;
            } else { consecutiveFree = 0; }
        }
        return -1; 
    }

    private String[] getMemoryArray() {
        String[] memArray = new String[40];
        for(int i = 0; i < 40; i++) memArray[i] = memory.read(i); return memArray;
    }

    private void printSystemState(PCB current) {
        StringBuilder log = new StringBuilder(); log.append("--- State at Clock ").append(clock).append(" ---\n");
        if (activeScheduler.equals("MLFQ")) {
            log.append("MLFQ Q0: "); for(PCB p : mlfq[0]) log.append("P").append(p.processID).append(" ");
            log.append("\nMLFQ Q1: "); for(PCB p : mlfq[1]) log.append("P").append(p.processID).append(" ");
            log.append("\nMLFQ Q2: "); for(PCB p : mlfq[2]) log.append("P").append(p.processID).append(" ");
            log.append("\nMLFQ Q3 (RR): "); for(PCB p : mlfq[3]) log.append("P").append(p.processID).append(" ");
        } else {
            log.append("Ready Queue: "); for(PCB p : readyQueue) log.append("P").append(p.processID).append(" ");
        }
        log.append("\nBlocked (Input): ").append(userInput.blockedQueue.size()); log.append("\nBlocked (Output): ").append(userOutput.blockedQueue.size()); log.append("\nBlocked (File): ").append(fileAccess.blockedQueue.size()).append("\n----------------------");
        
        if (current == null) {
            currentExecutingAddress = -1; 
        }

        if (gui != null) {
            String currentStr = current != null ? "P" + current.processID : "None";
            gui.updateDashboard(clock, currentStr, currentInstructionDisplay, currentExecutingAddress, getMemoryArray(), log.toString());
        }
    }
}