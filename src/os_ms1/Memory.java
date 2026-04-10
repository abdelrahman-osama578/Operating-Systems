package os_ms1;

import java.io.*;
import java.util.*;

public class Memory {
    private String[] mainMemory = new String[40];
    // Each process needs: lines of code + 3 variables + PCB data.
    // Let's assume a fixed block size per process for simplicity, e.g., 13 words.
    // 3 processes * 13 words = 39 words. It fits perfectly without swapping if they are small!
    // But we MUST handle swapping.

    public Memory() {
        Arrays.fill(mainMemory, null);
    }

    public String read(int address) {
        return mainMemory[address];
    }

    public void write(int address, String data) {
        mainMemory[address] = data;
    }

    // Swaps a process out of main memory into a text file
    public void swapToDisk(PCB pcb) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("disk_P" + pcb.processID + ".txt"))) {
            for (int i = pcb.minMemoryBoundary; i <= pcb.maxMemoryBoundary; i++) {
                writer.write(mainMemory[i] != null ? mainMemory[i] : "NULL");
                writer.newLine();
                mainMemory[i] = null; // Free memory
            }
            System.out.println("Process " + pcb.processID + " swapped to disk.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Swaps a process back into main memory
    public void swapFromDisk(PCB pcb, int newMinBoundary) {
        try (BufferedReader reader = new BufferedReader(new FileReader("disk_P" + pcb.processID + ".txt"))) {
            String line;
            int currentAddress = newMinBoundary;
            while ((line = reader.readLine()) != null) {
                mainMemory[currentAddress] = line.equals("NULL") ? null : line;
                currentAddress++;
            }
            // Update boundaries
            pcb.minMemoryBoundary = newMinBoundary;
            pcb.maxMemoryBoundary = currentAddress - 1;
            System.out.println("Process " + pcb.processID + " swapped into memory.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void printMemory() {
        System.out.println("--- Current Memory State ---");
        for(int i=0; i<40; i++) {
            System.out.println("Word " + i + ": " + mainMemory[i]);
        }
    }
}