package client;

import client.controller.ClientController;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        // Cài đặt Look and Feel sang giao diện hệ thống cho hiện đại
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        ClientController controller = new ClientController();
        controller.start();
    }
}
