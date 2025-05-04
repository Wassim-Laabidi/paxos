package rt4.paxos;

import rt4.paxos.gui.PaxosVisualizer;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Main launcher for the Paxos system.
 * This class starts both the GUI and the required server processes.
 */
public class PaxosLauncher {
    private static final int[] DEFAULT_PORTS = {50051, 50052, 50053};
    private static List<Process> serverProcesses = new ArrayList<>();

    public static void main(String[] args) {
        // Set better look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Show splash screen while starting servers
        showSplashScreen();

        // Start server processes
        CountDownLatch serversStarted = new CountDownLatch(DEFAULT_PORTS.length);
        for (int port : DEFAULT_PORTS) {
            startServerProcess(port, serversStarted);
        }

        // Wait for all servers to start
        try {
            serversStarted.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Launch the GUI
        SwingUtilities.invokeLater(() -> {
            PaxosVisualizer visualizer = new PaxosVisualizer();
            visualizer.setVisible(true);

            // Add shutdown hook to clean up server processes
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down Paxos servers...");
                stopAllServers();
            }));
        });
    }

    /**
     * Shows a splash screen while servers are starting
     */
    private static void showSplashScreen() {
        JFrame splashFrame = new JFrame("Starting Paxos...");
        splashFrame.setUndecorated(true);
        splashFrame.setSize(400, 200);
        splashFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Optimized Paxos Consensus");
        titleLabel.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        JLabel messageLabel = new JLabel("Starting server processes...");
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(titleLabel, java.awt.BorderLayout.NORTH);
        panel.add(progressBar, java.awt.BorderLayout.CENTER);
        panel.add(messageLabel, java.awt.BorderLayout.SOUTH);

        splashFrame.add(panel);
        splashFrame.setVisible(true);

        // Close splash after a delay
        Timer timer = new Timer(3000, e -> splashFrame.dispose());
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Starts a single server process on the specified port
     */
    private static void startServerProcess(int port, CountDownLatch latch) {
        new Thread(() -> {
            try {
                // Build command to start the server
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "java",
                        "-cp",
                        System.getProperty("java.class.path"),
                        "rt4.paxos.PaxosProposer",
                        String.valueOf(port));

                // Redirect error stream to output stream
                processBuilder.redirectErrorStream(true);

                // Start the process
                Process process = processBuilder.start();
                serverProcesses.add(process);

                // Handle output from the process
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    boolean serverStarted = false;

                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Server:" + port + "] " + line);

                        // Detect when server has started
                        if (line.contains("Server started on port") && !serverStarted) {
                            serverStarted = true;
                            latch.countDown();
                        }
                    }
                }

                // If we get here, the server has terminated
                System.out.println("Server on port " + port + " has terminated.");

            } catch (IOException e) {
                e.printStackTrace();
                latch.countDown(); // Ensure latch is decremented even on error
            }
        }).start();
    }

    /**
     * Stops all server processes
     */
    private static void stopAllServers() {
        for (Process process : serverProcesses) {
            if (process.isAlive()) {
                process.destroy();
            }
        }

        // Clear the list
        serverProcesses.clear();
    }
}