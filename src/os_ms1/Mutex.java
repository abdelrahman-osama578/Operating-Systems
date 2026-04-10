package os_ms1;

import java.util.LinkedList;
import java.util.Queue;

public class Mutex {
    public boolean isAvailable = true;
    public int ownerProcessID = -1;
    public Queue<PCB> blockedQueue = new LinkedList<>();
    private String name;

    public Mutex(String name) {
        this.name = name;
    }

    public boolean semWait(PCB p) {
        if (isAvailable) {
            isAvailable = false;
            ownerProcessID = p.processID;
            return true; // Acquired successfully
        } else {
            p.state = State.BLOCKED;
            blockedQueue.add(p);
            return false; // Process blocked
        }
    }

    public PCB semSignal(PCB p) {
        if (ownerProcessID == p.processID) {
            if (!blockedQueue.isEmpty()) {
                PCB unblockedProcess = blockedQueue.poll();
                unblockedProcess.state = State.READY;
                ownerProcessID = unblockedProcess.processID;
                return unblockedProcess; // Tell OS to move this to Ready Queue
            } else {
                isAvailable = true;
                ownerProcessID = -1;
            }
        }
        return null;
    }
}
