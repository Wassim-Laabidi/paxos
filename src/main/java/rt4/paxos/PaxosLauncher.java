package rt4.paxos;

import rt4.paxos.gui.PaxosVisualizer;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class PaxosLauncher {
    private static final int[] DEFAULT_PORTS = {50051, 50052, 50053};
    private static List<Process> serverProcesses = new ArrayList<>();
    private static boolean isShuttingDown = false;

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }


        JFrame splashFrame = showSplashScreen();

        // Start server processes
        CountDownLatch serversStarted = new CountDownLatch(DEFAULT_PORTS.length);
        for (int port : DEFAULT_PORTS) {
            startServerProcess(port, serversStarted);
        }

        // Wait for all servers to start with a timeout
        try {
            if (!serversStarted.await(10, TimeUnit.SECONDS)) {
                System.out.println("Warning: Not all servers started in time. Continuing anyway.");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        splashFrame.dispose();

        // Launch the GUI
        SwingUtilities.invokeLater(() -> {
            PaxosVisualizer visualizer = new PaxosVisualizer();
            visualizer.setVisible(true);

            // Add shutdown hook to clean up server processes
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down Paxos servers...");
                isShuttingDown = true;
                stopAllServers();
            }));
        });
    }


    private static JFrame showSplashScreen() {
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

        return splashFrame;
    }

     // Starts a single server process on the specified port

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

                        // Break if shutting down
                        if (isShuttingDown) {
                            break;
                        }
                    }
                }

                // the server has terminated
                System.out.println("Server on port " + port + " has terminated.");

            } catch (IOException e) {
                System.err.println("Error starting server on port " + port + ": " + e.getMessage());
                e.printStackTrace();
                latch.countDown(); // Ensure latch is decremented even on error
            }
        }).start();
    }


     // Method to start additional servers for the 5-node mode

    public static void startAdditionalServers(int[] additionalPorts) {
        CountDownLatch additionalLatch = new CountDownLatch(additionalPorts.length);

        for (int port : additionalPorts) {
            startServerProcess(port, additionalLatch);
        }

        // Wait for additional servers to start
        try {
            if (!additionalLatch.await(5, TimeUnit.SECONDS)) {
                System.out.println("Warning: Not all additional servers started in time");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


     // Stops all server processes

    private static void stopAllServers() {
        for (Process process : serverProcesses) {
            if (process.isAlive()) {
                process.destroy();
                try {
                    // Wait for process to terminate gracefully
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        // Force termination if it doesn't exit nicely
                        process.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    process.destroyForcibly();
                }
            }
        }

        // Clear the list
        serverProcesses.clear();
    }
}