package os_ms1;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Comparator;

public class OSDashboard extends JFrame {
    
    private final Color BG_DARK = new Color(15, 23, 36);       
    private final Color PANEL_DARK = new Color(25, 39, 52, 220); 
    private final Color ACCENT_BLUE = new Color(0, 191, 255);  
    private final Color TEXT_LIGHT = new Color(245, 248, 250); 
    private final Color BORDER_COLOR = new Color(56, 68, 77);

    private JLabel clockLabel;
    private JLabel currentProcessLabel;
    private JLabel currentInstructionLabel; 
    private JTextArea logArea;
    private JTable memoryTable;
    private DefaultTableModel memoryTableModel;
    private JComboBox<String> schedulerDropdown;
    private JButton autoRunButton;
    
    private JTextField timeSliceInput;
    private JTextField p1ArrivalInput;
    private JTextField p2ArrivalInput;
    private JTextField p3ArrivalInput;
    
    private OperatingSystem os;
    private Thread osThread;
    
    private int currentExecutingAddress = -1;
    
    // NEW: Boolean flag to track the current display format of the Address column
    private boolean isHexFormat = true;

    public OSDashboard() {
        setTitle("Project X - OS Simulator");
        setSize(1200, 850); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 
        
        ArtisticBackgroundPanel mainContent = new ArtisticBackgroundPanel();
        mainContent.setLayout(new BorderLayout(15, 15));
        mainContent.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(mainContent);

        mainContent.add(createTopPanel(), BorderLayout.NORTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createMemoryPanel(), createSidePanel());
        splitPane.setResizeWeight(0.5); 
        splitPane.setOpaque(false);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);
        mainContent.add(splitPane, BorderLayout.CENTER);
        
        mainContent.add(createBottomPanel(), BorderLayout.SOUTH);
    }

    public void bootOS() {
        if (os != null) os.shutdown(); 
        
        logArea.setText(">>> SYSTEM REBOOTING...\n");
        autoRunButton.setBackground(new Color(46, 204, 113));
        autoRunButton.setText("AUTO-RUN");
        
        String selectedMode = (String) schedulerDropdown.getSelectedItem();
        
        int ts = Integer.parseInt(timeSliceInput.getText().trim());
        int arr1 = Integer.parseInt(p1ArrivalInput.getText().trim());
        int arr2 = Integer.parseInt(p2ArrivalInput.getText().trim());
        int arr3 = Integer.parseInt(p3ArrivalInput.getText().trim());
        
        os = new OperatingSystem(this, selectedMode, ts, arr1, arr2, arr3);
        
        osThread = new Thread(() -> {
            if (selectedMode.equals("Round Robin")) os.runSchedulerRR(); 
            else if (selectedMode.equals("HRRN")) os.runSchedulerHRRN();
            else os.runSchedulerMLFQ();
        });
        osThread.start();
    }

    public void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength()); 
    }

    public void showMetricsReport(String scheduler, List<OperatingSystem.GanttRecord> timeline, List<PCB> processes) {
        JDialog dialog = new JDialog(this, "System Evaluation: " + scheduler, false);
        dialog.setSize(950, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());
        
        JPanel chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBackground(BG_DARK);
        chartContainer.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ACCENT_BLUE), 
                "Graphical Gantt Chart", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 16), ACCENT_BLUE));
        
        JPanel ganttPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(BG_DARK); g2d.fillRect(0, 0, getWidth(), getHeight());

                if (timeline == null || timeline.isEmpty()) return;
                int totalTime = timeline.get(timeline.size()-1).end;
                if (totalTime == 0) return;

                int width = getWidth() - 40; int startX = 20; int startY = 40;
                g2d.setColor(BORDER_COLOR);
                for(int i=0; i<=totalTime; i++) {
                    int x = startX + (int)((i / (double)totalTime) * width);
                    g2d.drawLine(x, 20, x, startY + 60);
                    if (i % 5 == 0 || i == totalTime) { 
                        g2d.setColor(TEXT_LIGHT); g2d.drawString(String.valueOf(i), x - 5, startY + 75); g2d.setColor(BORDER_COLOR);
                    }
                }

                for (OperatingSystem.GanttRecord r : timeline) {
                    int rectX = startX + (int)((r.start / (double)totalTime) * width);
                    int rectW = (int)(((r.end - r.start) / (double)totalTime) * width);

                    if (r.pid == 0) g2d.setColor(new Color(50, 50, 50)); 
                    else if (r.pid == 1) g2d.setColor(new Color(231, 76, 60)); 
                    else if (r.pid == 2) g2d.setColor(new Color(52, 152, 219)); 
                    else if (r.pid == 3) g2d.setColor(new Color(46, 204, 113)); 
                    else g2d.setColor(ACCENT_BLUE);

                    g2d.fillRect(rectX, startY, rectW, 40); g2d.setColor(Color.WHITE); g2d.drawRect(rectX, startY, rectW, 40);
                    String label = r.pid == 0 ? "IDLE" : "P" + r.pid;
                    g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
                    if (rectW > 30) g2d.drawString(label, rectX + (rectW/2) - 10, startY + 25);
                }
            }
        };
        ganttPanel.setPreferredSize(new Dimension(900, 150));
        chartContainer.add(ganttPanel, BorderLayout.CENTER);
        
        String[] columns = {"PID", "Arrival", "Burst", "Start", "Finish", "Response", "Turnaround", "Waiting", "CPU Util"};
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        double tResp = 0, tTurn = 0, tWait = 0, tUtil = 0; int count = 0;
        
        processes.sort(Comparator.comparingInt(p -> p.processID));
        for (PCB p : processes) {
            if (p.finishTime == -1) continue;
            int actualBurst = p.cpuCycles; 
            int response = p.startTime - p.arrivalTime; int turnaround = p.finishTime - p.arrivalTime;
            int waiting = turnaround - actualBurst; double util = turnaround == 0 ? 0 : actualBurst / (double) turnaround;
            model.addRow(new Object[]{ "P" + p.processID, p.arrivalTime, actualBurst, p.startTime, p.finishTime, 
                                       response, turnaround, waiting, String.format("%.2f", util) });
            tResp += response; tTurn += turnaround; tWait += waiting; tUtil += util; count++;
        }
        
        if (count > 0) model.addRow(new Object[]{ "AVG", "-", "-", "-", "-", String.format("%.2f", tResp/count), 
                                       String.format("%.2f", tTurn/count), String.format("%.2f", tWait/count), String.format("%.2f", tUtil/count) });

        JTable table = new JTable(model);
        table.setBackground(BG_DARK); table.setForeground(TEXT_LIGHT); table.setGridColor(BORDER_COLOR);
        table.getTableHeader().setBackground(PANEL_DARK); table.getTableHeader().setForeground(ACCENT_BLUE);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16));
        table.setRowHeight(32); table.setFont(new Font("Monospaced", Font.PLAIN, 16));
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(BG_DARK);
        scrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ACCENT_BLUE), 
                "Performance Table", TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 16), ACCENT_BLUE));

        dialog.add(chartContainer, BorderLayout.NORTH); dialog.add(scrollPane, BorderLayout.CENTER); dialog.setVisible(true);
    }
    
    private class ArtisticBackgroundPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g; g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(BG_DARK); g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(new Color(29, 155, 240, 15)); Path2D.Double poly1 = new Path2D.Double();
            poly1.moveTo(0, getHeight() * 0.8); poly1.lineTo(getWidth() * 0.6, 0); poly1.lineTo(0, 0); poly1.closePath(); g2d.fill(poly1);
            GradientPaint gp = new GradientPaint(getWidth(), getHeight(), new Color(0, 191, 255, 30), getWidth() / 2f, getHeight() / 2f, new Color(15, 23, 36, 0));
            g2d.setPaint(gp); g2d.fillOval(getWidth() - 400, getHeight() - 400, 600, 600); g2d.setColor(new Color(255, 255, 255, 5));
            for(int i = 0; i < getWidth(); i += 40) g2d.drawLine(i, 0, i - 200, getHeight());
        }
    }

    public String requestInput(int processId, String varName) {
        JDialog dialog = new JDialog(this, "Input Required", true);
        dialog.setUndecorated(true); dialog.setSize(450, 220); dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 20)); panel.setBackground(PANEL_DARK);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 2), new EmptyBorder(20, 20, 20, 20)));

        JLabel title = new JLabel("PROCESS " + processId + " AWAITING INPUT", SwingConstants.CENTER);
        title.setForeground(ACCENT_BLUE); title.setFont(new Font("SansSerif", Font.BOLD, 18)); 
        JLabel prompt = new JLabel("Please enter a value for variable: " + varName, SwingConstants.CENTER);
        prompt.setForeground(TEXT_LIGHT); prompt.setFont(new Font("SansSerif", Font.PLAIN, 16)); 

        JTextField inputField = new JTextField(); inputField.setBackground(BG_DARK); inputField.setForeground(ACCENT_BLUE);
        inputField.setFont(new Font("Monospaced", Font.BOLD, 22)); inputField.setCaretColor(TEXT_LIGHT);
        inputField.setHorizontalAlignment(JTextField.CENTER); inputField.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JButton submitBtn = createStyledButton("SUBMIT DATA"); final String[] result = new String[1]; 
        submitBtn.addActionListener(e -> { result[0] = inputField.getText(); dialog.dispose(); }); inputField.addActionListener(e -> submitBtn.doClick()); 

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 10, 10)); centerPanel.setOpaque(false); centerPanel.add(prompt); centerPanel.add(inputField);
        panel.add(title, BorderLayout.NORTH); panel.add(centerPanel, BorderLayout.CENTER); panel.add(submitBtn, BorderLayout.SOUTH);
        dialog.setContentPane(panel); dialog.setVisible(true); 

        return result[0] != null && !result[0].trim().isEmpty() ? result[0] : "0";
    }

    private JPanel createTopPanel() {
        JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setOpaque(false); 
        
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        configPanel.setOpaque(false);
        configPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        
        schedulerDropdown = new JComboBox<>(new String[]{"Round Robin", "HRRN", "MLFQ"});
        schedulerDropdown.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        timeSliceInput = createConfigInput("2");
        p1ArrivalInput = createConfigInput("0");
        p2ArrivalInput = createConfigInput("1");
        p3ArrivalInput = createConfigInput("4");

        configPanel.add(new JLabel("<html><font color='white'>Scheduler:</font></html>")); configPanel.add(schedulerDropdown);
        configPanel.add(new JLabel("<html><font color='white'> Time Slice:</font></html>")); configPanel.add(timeSliceInput);
        configPanel.add(new JLabel("<html><font color='white'> P1 Arrival:</font></html>")); configPanel.add(p1ArrivalInput);
        configPanel.add(new JLabel("<html><font color='white'> P2 Arrival:</font></html>")); configPanel.add(p2ArrivalInput);
        configPanel.add(new JLabel("<html><font color='white'> P3 Arrival:</font></html>")); configPanel.add(p3ArrivalInput);
        
        JPanel statusPanel = new JPanel(new GridLayout(1, 3)); statusPanel.setOpaque(false);
        statusPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        clockLabel = new JLabel("System Clock: 0"); styleLabel(clockLabel, 22, true); 
        currentInstructionLabel = new JLabel("Instruction: None", SwingConstants.CENTER); styleLabel(currentInstructionLabel, 18, false); currentInstructionLabel.setForeground(new Color(243, 156, 18));
        currentProcessLabel = new JLabel("Running: None", SwingConstants.RIGHT); styleLabel(currentProcessLabel, 20, false); 
        
        statusPanel.add(clockLabel); statusPanel.add(currentInstructionLabel); statusPanel.add(currentProcessLabel);
        
        wrapper.add(configPanel, BorderLayout.NORTH);
        wrapper.add(statusPanel, BorderLayout.CENTER);
        return wrapper;
    }
    
    private JTextField createConfigInput(String defaultVal) {
        JTextField tf = new JTextField(defaultVal, 3);
        tf.setBackground(PANEL_DARK); tf.setForeground(ACCENT_BLUE);
        tf.setFont(new Font("SansSerif", Font.BOLD, 14)); tf.setHorizontalAlignment(JTextField.CENTER);
        return tf;
    }

    private void toggleAddressFormat() {
        isHexFormat = !isHexFormat;
        for (int i = 0; i < 40; i++) {
            memoryTableModel.setValueAt(isHexFormat ? String.format("0x%04X", i) : String.valueOf(i), i, 0);
        }
        memoryTable.repaint();
    }

    private JPanel createMemoryPanel() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setBackground(PANEL_DARK);
        panel.setBorder(createStyledTitledBorder("MAIN MEMORY / 40 WORDS"));
        String[] columnNames = {"Address", "Data Content"}; memoryTableModel = new DefaultTableModel(columnNames, 40);
        memoryTable = new JTable(memoryTableModel);
        
        memoryTable.setBackground(BG_DARK); memoryTable.setForeground(TEXT_LIGHT); memoryTable.setGridColor(BORDER_COLOR); 
        memoryTable.getTableHeader().setBackground(PANEL_DARK); memoryTable.getTableHeader().setForeground(ACCENT_BLUE);
        memoryTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 16)); 
        memoryTable.setRowHeight(32); memoryTable.setFont(new Font("Monospaced", Font.PLAIN, 16)); 

        memoryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (row == currentExecutingAddress) {
                    c.setBackground(new Color(41, 128, 185)); 
                    c.setForeground(Color.WHITE);
                    setFont(new Font("Monospaced", Font.BOLD, 16));
                } else {
                    c.setBackground(BG_DARK); 
                    c.setForeground(TEXT_LIGHT);
                    setFont(new Font("Monospaced", Font.PLAIN, 16));
                }
                return c;
            }
        });

        for (int i = 0; i < 40; i++) { 
            memoryTableModel.setValueAt(String.format("0x%04X", i), i, 0); 
            memoryTableModel.setValueAt("NULL", i, 1); 
        }

        // --- NEW: Add Mouse Listeners to toggle Address Format ---
        
        // 1. Listen for clicks on the Table Cells
        memoryTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = memoryTable.columnAtPoint(e.getPoint());
                if (col == 0) { // Only toggle if they click the Address column
                    toggleAddressFormat();
                }
            }
        });

        // 2. Listen for clicks on the Table Header ("Address" title)
        memoryTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = memoryTable.columnAtPoint(e.getPoint());
                if (col == 0) {
                    toggleAddressFormat();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(memoryTable); scrollPane.getViewport().setBackground(BG_DARK);
        scrollPane.setBorder(null); panel.add(scrollPane, BorderLayout.CENTER); return panel;
    }

    private JPanel createSidePanel() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setBackground(PANEL_DARK);
        panel.setBorder(createStyledTitledBorder("SYSTEM LOGS & QUEUES"));
        logArea = new JTextArea(); logArea.setBackground(BG_DARK); logArea.setForeground(new Color(200, 220, 240));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 16)); logArea.setEditable(false); logArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(logArea); scrollPane.setBorder(null); panel.add(scrollPane, BorderLayout.CENTER); return panel;
    }

    private JPanel createBottomPanel() {
        JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setOpaque(false);
        JPanel creditsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)); creditsPanel.setOpaque(false);
        JLabel credits = new JLabel("ENGINEERED BY: Abdelrahman Osama | Mohannad Tamer | Abdelrahman El-Zeiny | Abdullah Wessam");
        credits.setFont(new Font("SansSerif", Font.BOLD, 13)); credits.setForeground(new Color(150, 160, 170)); creditsPanel.add(credits);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10)); controlsPanel.setOpaque(false);
        JButton rebootButton = createStyledButton("REBOOT OS"); rebootButton.setBackground(new Color(243, 156, 18)); rebootButton.addActionListener(e -> bootOS());
        JButton stepButton = createStyledButton("EXECUTE CYCLE"); stepButton.addActionListener(e -> { if (os != null) os.stepCycle(); });
        autoRunButton = createStyledButton("AUTO-RUN"); autoRunButton.setBackground(new Color(46, 204, 113)); 
        autoRunButton.addActionListener(e -> {
            if (os != null) {
                os.toggleAutoRun();
                if (os.isAutoRun) { autoRunButton.setBackground(new Color(231, 76, 60)); autoRunButton.setText("STOP AUTO"); } 
                else { autoRunButton.setBackground(new Color(46, 204, 113)); autoRunButton.setText("AUTO-RUN"); }
            }
        });
        
        controlsPanel.add(rebootButton); controlsPanel.add(stepButton); controlsPanel.add(autoRunButton);
        wrapper.add(creditsPanel, BorderLayout.WEST); wrapper.add(controlsPanel, BorderLayout.EAST);
        wrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR)); return wrapper;
    }

    private void styleLabel(JLabel label, int fontSize, boolean bold) { label.setForeground(TEXT_LIGHT); label.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, fontSize)); }
    private TitledBorder createStyledTitledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(ACCENT_BLUE), title);
        border.setTitleColor(ACCENT_BLUE); border.setTitleFont(new Font("SansSerif", Font.BOLD, 16)); return border;
    }
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text); button.setBackground(ACCENT_BLUE); button.setForeground(Color.WHITE); button.setFocusPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 16)); button.setBorder(new EmptyBorder(10, 25, 10, 25));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR)); return button;
    }

    public void updateDashboard(int currentClock, String currentProcess, String currentInstruction, int activeMemoryAddress, String[] memoryState, String logs) {
        clockLabel.setText("SYSTEM CLOCK: " + currentClock);
        currentProcessLabel.setText("ACTIVE THREAD: " + currentProcess);
        currentInstructionLabel.setText("Instruction: " + currentInstruction);
        appendLog(logs); 
        
        for (int i = 0; i < 40; i++) {
            // Apply the format based on the user's toggle state!
            memoryTableModel.setValueAt(isHexFormat ? String.format("0x%04X", i) : String.valueOf(i), i, 0); 
            memoryTableModel.setValueAt(memoryState[i] == null ? "NULL" : memoryState[i], i, 1);
        }
        
        this.currentExecutingAddress = activeMemoryAddress;
        memoryTable.repaint();
        
        if (activeMemoryAddress >= 0 && activeMemoryAddress < 40) {
            memoryTable.scrollRectToVisible(memoryTable.getCellRect(activeMemoryAddress, 0, true));
        }
    }
    
    public void showSwapNotification(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OSDashboard gui = new OSDashboard(); gui.setVisible(true); gui.bootOS(); 
        });
    }
}