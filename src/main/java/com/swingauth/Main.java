package com.swingauth;

import com.swingauth.ui.AuthFrame;

import javax.swing.*;

public class Main {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {}
      new AuthFrame().setVisible(true);
    });
  }
}
