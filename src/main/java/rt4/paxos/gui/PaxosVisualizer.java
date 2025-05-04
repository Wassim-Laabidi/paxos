package rt4.paxos.gui;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PaxosVisualizer extends JFrame {
    // Constants
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int NODE_RADIUS = 60;
    private static final Color BACKGROUND_COLOR = new Color(240, 240, 245);
    private static final Color NODE_COLOR = new Color(220, 220, 220);
    private static final Color LEADER_COLOR = new Color(155, 187, 235);
    private static final Color PROPOSER_COLOR = new Color(252, 186, 3);
    private static final Color ACCEPTOR_COLOR = new Color(181, 234, 171);
    private static final Color TEXT_COLOR = new Color(50, 50, 50);
    private static final Color MESSAGE_COLOR = new Color(252, 146, 162);
    private static final Color SUCCESS_COLOR = new Color(140, 200, 140);

    // Server information
    private final ConcurrentHashMap<String, ServerNode> nodes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<LogMessage> logMessages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<MessageAnimation> messageAnimations = new CopyOnWriteArrayList<>();

    // UI components
    private JPanel visualizationPanel;
    private JTextArea logTextArea;
    private JButton startButton;
    private JButton stopButton;
    private JComboBox<String> portSelector;
    private JLabel statusLabel;
    private JLabel phaseLabel;
    private JLabel consensusLabel;

    // State
    private String currentPhase = "Ready";
    private int consensusValue = -1;
    private String leaderId = "None";
    private Timer animationTimer;
    private PaxosController controller;
    private boolean running = false;

    public PaxosVisualizer() {
        super("Paxos Consensus Visualization");
        setupUI();
        initializeNodes();
        controller = new PaxosController(this);
    }

    private void setupUI() {
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top panel with controls
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(new Color(230, 230, 235));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        startButton = new JButton("Start Paxos Process");
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);

        String[] ports = {"3 Nodes (50051-50053)", "5 Nodes (50051-50055)"};
        portSelector = new JComboBox<>(ports);

        statusLabel = new JLabel("Status: Ready");
        phaseLabel = new JLabel("Phase: —");
        consensusLabel = new JLabel("Consensus Value: —");

        controlPanel.add(new JLabel("Configuration:"));
        controlPanel.add(portSelector);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);
        controlPanel.add(statusLabel);
        controlPanel.add(phaseLabel);
        controlPanel.add(consensusLabel);

        // Main visualization panel
        visualizationPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setBackground(BACKGROUND_COLOR);
                g2d.clearRect(0, 0, getWidth(), getHeight());

                // Draw connections between nodes
                drawConnections(g2d);

                // Draw nodes
                for (ServerNode node : nodes.values()) {
                    drawNode(g2d, node);
                }

                // Draw message animations
                for (MessageAnimation animation : messageAnimations) {
                    drawMessageAnimation(g2d, animation);
                }
            }
        };
        visualizationPanel.setBackground(BACKGROUND_COLOR);

        // Log panel
        logTextArea = new JTextArea(5, 50);
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logTextArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Event Log"));

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(visualizationPanel, BorderLayout.CENTER);
        add(logScrollPane, BorderLayout.SOUTH);

        // Event listeners
        startButton.addActionListener(e -> startProcess());
        stopButton.addActionListener(e -> stopProcess());

        // Animation timer
        animationTimer = new Timer(50, e -> {
            updateAnimations();
            visualizationPanel.repaint();
        });
        animationTimer.start();

        setLocationRelativeTo(null);
    }

    private void initializeNodes() {
        // Default 3-node setup
        addNode("50051", 250, 200);
        addNode("50052", 500, 200);
        addNode("50053", 375, 400);
    }

    private void addNode(String id, int x, int y) {
        nodes.put(id, new ServerNode(id, x, y));
    }

    private void resetNodes() {
        nodes.clear();

        // Based on selected configuration
        if (portSelector.getSelectedIndex() == 0) {
            // 3 Nodes
            addNode("50051", 250, 200);
            addNode("50052", 500, 200);
            addNode("50053", 375, 400);
        } else {
            // 5 Nodes - arrange in a pentagon
            int centerX = WIDTH / 2 - 100;
            int centerY = HEIGHT / 2 - 100;
            int radius = 200;

            for (int i = 0; i < 5; i++) {
                double angle = Math.PI / 2 + i * (2 * Math.PI / 5);
                int x = (int) (centerX + radius * Math.cos(angle));
                int y = (int) (centerY + radius * Math.sin(angle));
                addNode("5005" + (i + 1), x, y);
            }
        }
    }

    private void drawNode(Graphics2D g2d, ServerNode node) {
        // Determine node color based on state
        Color nodeColor = NODE_COLOR;
        if (node.isLeader) {
            nodeColor = LEADER_COLOR;
        } else if (node.role.equals("Proposer")) {
            nodeColor = PROPOSER_COLOR;
        } else if (node.role.equals("Acceptor")) {
            nodeColor = ACCEPTOR_COLOR;
        }

        // Draw node
        g2d.setColor(nodeColor);
        g2d.fillOval(node.x - NODE_RADIUS, node.y - NODE_RADIUS,
                NODE_RADIUS * 2, NODE_RADIUS * 2);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2f));
        g2d.drawOval(node.x - NODE_RADIUS, node.y - NODE_RADIUS,
                NODE_RADIUS * 2, NODE_RADIUS * 2);

        // Draw node info
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        String nodeId = "Server " + node.id;
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(nodeId);
        g2d.drawString(nodeId, node.x - textWidth / 2, node.y - 5);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        String valueStr = "Value: " + node.currentValue;
        textWidth = fm.stringWidth(valueStr);
        g2d.drawString(valueStr, node.x - textWidth / 2, node.y + 15);

        // Show proposal number if relevant
        if (node.proposalNumber > 0) {
            String propStr = "Prop #: " + node.proposalNumber;
            textWidth = fm.stringWidth(propStr);
            g2d.drawString(propStr, node.x - textWidth / 2, node.y + 30);
        }
    }

    private void drawConnections(Graphics2D g2d) {
        List<ServerNode> nodeList = new ArrayList<>(nodes.values());

        g2d.setColor(new Color(200, 200, 200));
        g2d.setStroke(new BasicStroke(1.5f));

        // Draw lines connecting all nodes
        for (int i = 0; i < nodeList.size(); i++) {
            for (int j = i + 1; j < nodeList.size(); j++) {
                ServerNode node1 = nodeList.get(i);
                ServerNode node2 = nodeList.get(j);
                g2d.drawLine(node1.x, node1.y, node2.x, node2.y);
            }
        }
    }

    private void drawMessageAnimation(Graphics2D g2d, MessageAnimation anim) {
        g2d.setColor(anim.isSuccessful ? SUCCESS_COLOR : MESSAGE_COLOR);
        g2d.setStroke(new BasicStroke(2.5f));

        int size = 12;
        g2d.fillOval(anim.currentX - size/2, anim.currentY - size/2, size, size);

        // Draw message content if relevant
        if (anim.messageType != null && !anim.messageType.isEmpty()) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2d.drawString(anim.messageType, anim.currentX + size, anim.currentY);
        }
    }

    private void updateAnimations() {
        // Create a list to store completed animations
        List<MessageAnimation> completedAnimations = new ArrayList<>();

        // Identify completed animations
        for (MessageAnimation anim : messageAnimations) {
            anim.update();
            if (anim.isComplete()) {
                completedAnimations.add(anim);
            }
        }

        // Remove completed animations
        if (!completedAnimations.isEmpty()) {
            messageAnimations.removeAll(completedAnimations);
        }
    }

    public void addMessageAnimation(String fromId, String toId, String messageType, boolean isSuccessful) {
        ServerNode from = nodes.get(fromId);
        ServerNode to = nodes.get(toId);

        if (from != null && to != null) {
            messageAnimations.add(new MessageAnimation(from.x, from.y, to.x, to.y, messageType, isSuccessful));
        }
    }

    public void addLogMessage(String event, String description) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        LogMessage logMsg = new LogMessage(timestamp, event, description);
        logMessages.add(0, logMsg);  // Add to beginning

        // Update log text area
        SwingUtilities.invokeLater(() -> {
            logTextArea.setText("");
            StringBuilder logText = new StringBuilder();
            int count = 0;
            for (LogMessage msg : logMessages) {
                logText.append(msg.timestamp).append(" [").append(msg.event)
                        .append("] ").append(msg.description).append("\n");
                count++;
                if (count >= 100) break;  // Limit to 100 messages
            }
            logTextArea.setText(logText.toString());
            logTextArea.setCaretPosition(0);
        });
    }

    public void updateNodeStatus(String nodeId, boolean isLeader, String role,
                                 int proposalNumber, int currentValue) {
        ServerNode node = nodes.get(nodeId);
        if (node != null) {
            node.isLeader = isLeader;
            node.role = role;
            node.proposalNumber = proposalNumber;
            node.currentValue = currentValue;

            if (isLeader) {
                leaderId = nodeId;
            }

            visualizationPanel.repaint();
        }
    }

    public void setPhase(String phase) {
        currentPhase = phase;
        SwingUtilities.invokeLater(() -> {
            phaseLabel.setText("Phase: " + phase);
        });
    }

    public void setConsensusValue(int value) {
        consensusValue = value;
        SwingUtilities.invokeLater(() -> {
            consensusLabel.setText("Consensus Value: " + value);
        });
    }

    private void startProcess() {
        resetNodes();
        logMessages.clear();
        messageAnimations.clear();
        consensusValue = -1;
        leaderId = "None";
        setPhase("Starting");

        addLogMessage("SYSTEM", "Starting Paxos consensus process");
        running = true;

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusLabel.setText("Status: Running");

        // Extract selected ports
        List<String> targetPorts;
        if (portSelector.getSelectedIndex() == 0) {
            targetPorts = Arrays.asList("50051", "50052", "50053");
        } else {
            targetPorts = Arrays.asList("50051", "50052", "50053", "50054", "50055");
        }

        // Start Paxos process in background thread
        new Thread(() -> {
            controller.startPaxosProcess(targetPorts);
        }).start();
    }

    private void stopProcess() {
        running = false;
        controller.stopPaxosProcess();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("Status: Stopped");
        addLogMessage("SYSTEM", "Paxos process stopped by user");
    }

    public boolean isRunning() {
        return running;
    }

    // Inner classes
    static class ServerNode {
        final String id;
        int x, y;
        boolean isLeader = false;
        String role = "Unknown";
        int proposalNumber = 0;
        int currentValue = -1;

        public ServerNode(String id, int x, int y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    static class MessageAnimation {
        final int startX, startY, endX, endY;
        int currentX, currentY;
        final String messageType;
        final boolean isSuccessful;
        double progress = 0.0;
        final double speed = 0.05;  // Speed of animation

        public MessageAnimation(int startX, int startY, int endX, int endY,
                                String messageType, boolean isSuccessful) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.currentX = startX;
            this.currentY = startY;
            this.messageType = messageType;
            this.isSuccessful = isSuccessful;
        }

        public void update() {
            progress += speed;
            if (progress > 1.0) progress = 1.0;

            currentX = (int) (startX + (endX - startX) * progress);
            currentY = (int) (startY + (endY - startY) * progress);
        }

        public boolean isComplete() {
            return progress >= 1.0;
        }
    }

    static class LogMessage {
        final String timestamp;
        final String event;
        final String description;

        public LogMessage(String timestamp, String event, String description) {
            this.timestamp = timestamp;
            this.event = event;
            this.description = description;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new PaxosVisualizer().setVisible(true);
        });
    }
}