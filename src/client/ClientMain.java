package client;

import client.controller.ClientController;
import client.ui.Theme;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        Theme.install();

        ClientController controller = new ClientController();
        controller.start();
    }
}
