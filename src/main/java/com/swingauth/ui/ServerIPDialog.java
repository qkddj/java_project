package com.swingauth.ui;

import com.swingauth.config.ServerConfig;

import javax.swing.*;
import java.awt.*;

/**
 * 서버 IP 주소를 입력받는 다이얼로그
 */
public class ServerIPDialog extends JDialog {
    private JTextField ipField;
    private JTextField portField;
    private String serverIP;
    private int serverPort;
    private boolean confirmed = false;

    public ServerIPDialog(JFrame parent) {
        super(parent, "서버 주소 설정", true);
        setSize(400, 200);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        
        // 현재 설정된 서버 주소 가져오기
        String currentHost = ServerConfig.getServerHost();
        int currentPort = ServerConfig.getServerPort();
        
        // 중앙 패널
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // IP 주소 입력
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel ipLabel = new JLabel("서버 IP 주소:");
        centerPanel.add(ipLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        ipField = new JTextField(currentHost != null ? currentHost : "localhost", 15);
        centerPanel.add(ipField, gbc);
        
        // 포트 번호 입력
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel portLabel = new JLabel("포트 번호:");
        centerPanel.add(portLabel, gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        portField = new JTextField(String.valueOf(currentPort), 10);
        centerPanel.add(portField, gbc);
        
        // 안내 메시지
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel infoLabel = new JLabel("<html><small>예: 192.168.0.100 또는 localhost</small></html>");
        infoLabel.setForeground(Color.GRAY);
        centerPanel.add(infoLabel, gbc);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // 하단 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("확인");
        JButton cancelButton = new JButton("취소");
        
        okButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();
            
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "IP 주소를 입력하세요.", "오류", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    JOptionPane.showMessageDialog(this, "포트 번호는 1-65535 사이여야 합니다.", "오류", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                serverIP = ip;
                serverPort = port;
                confirmed = true;
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "포트 번호는 숫자여야 합니다.", "오류", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // 테마 적용
        applyTheme();
    }
    
    private void applyTheme() {
        boolean isDarkMode = ThemeManager.getInstance().isDarkMode();
        
        getContentPane().setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
        
        Component[] components = getContentPane().getComponents();
        for (Component comp : components) {
            if (comp instanceof JPanel) {
                ((JPanel) comp).setBackground(isDarkMode ? ThemeManager.DARK_BG : ThemeManager.LIGHT_BG);
                applyThemeToComponent(comp, isDarkMode);
            }
        }
    }
    
    private void applyThemeToComponent(Component comp, boolean isDarkMode) {
        if (comp instanceof JLabel) {
            ((JLabel) comp).setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        } else if (comp instanceof JTextField) {
            ((JTextField) comp).setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
            ((JTextField) comp).setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        } else if (comp instanceof JButton) {
            ((JButton) comp).setBackground(isDarkMode ? ThemeManager.DARK_BG2 : ThemeManager.LIGHT_BG2);
            ((JButton) comp).setForeground(isDarkMode ? ThemeManager.TEXT_LIGHT : ThemeManager.TEXT_DARK);
        } else if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyThemeToComponent(child, isDarkMode);
            }
        }
    }
    
    public String getServerIP() {
        return serverIP;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    /**
     * 다이얼로그를 표시하고 사용자가 확인했는지 반환
     */
    public static boolean showDialog(JFrame parent) {
        ServerIPDialog dialog = new ServerIPDialog(parent);
        dialog.setVisible(true);
        
        if (dialog.isConfirmed()) {
            ServerConfig.setServerHost(dialog.getServerIP());
            ServerConfig.setServerPort(dialog.getServerPort());
            return true;
        }
        return false;
    }
}

