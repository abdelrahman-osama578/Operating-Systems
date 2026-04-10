package os_ms1;

public class PCB {
    public int processID;
    public State state;
    public int programCounter;
    public int minMemoryBoundary;
    public int maxMemoryBoundary;
    
    public int arrivalTime;
    public int burstTime; // Initial instruction count
    public int priorityLevel; 
    public int quantumUsed;   
    
    // --- NEW METRICS FOR PERFORMANCE TABLE ---
    public int startTime = -1;
    public int finishTime = -1;
    public int cpuCycles = 0; // True Burst Time (Includes cycles wasted while trying to acquire a blocked Mutex)
    
    public PCB(int id, int minBound, int maxBound, int arrivalTime, int burstTime) {
        this.processID = id;
        this.state = State.READY;
        this.programCounter = 0;
        this.minMemoryBoundary = minBound;
        this.maxMemoryBoundary = maxBound;
        
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priorityLevel = 0; 
        this.quantumUsed = 0;
    }
}