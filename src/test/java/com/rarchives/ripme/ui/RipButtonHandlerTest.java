package com.rarchives.ripme.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class RipButtonHandlerTest {

    @Test
    public void testDuplicateUrl() throws IOException {
        MainWindow mainWindow = new MainWindow();
        MainWindow.RipButtonHandler ripButtonHandler = new MainWindow.RipButtonHandler(mainWindow);

        // Set up the initial conditions with a URL
        String testUrl = "http://example.com";
        mainWindow.getRipTextfield().setText(testUrl);
        ripButtonHandler.actionPerformed(null);

        // Verify that the URL is added to the queueListModel
        assertTrue(mainWindow.getQueueListModel().contains(testUrl));

        // Save the current state of queueListModel
        int initialQueueSize = mainWindow.getQueueListModel().size();

        // Set up the conditions with the same URL again
        mainWindow.getRipTextfield().setText(testUrl);
        ripButtonHandler.actionPerformed(null);

        // Verify that the queueListModel size remains unchanged
        assertEquals(initialQueueSize, mainWindow.getQueueListModel().size());
        assertEquals(mainWindow.getRipTextfield().getText(), "");

    }
}