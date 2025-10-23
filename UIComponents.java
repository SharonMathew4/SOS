import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class UIComponents {
    public static JPanel createNavItem(String text, CardLayout cardLayout, JPanel contentPanel) {
        JPanel item = new JPanel();
        item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
        item.setBackground(AppStyles.SIDEBAR_COLOR);
        item.setBorder(new EmptyBorder(10, 15, 10, 15));
        item.setMaximumSize(new Dimension(200, 40));
        
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(AppStyles.getAppFont(Font.PLAIN, 14f));
        textLabel.setForeground(AppStyles.TEXT_COLOR);
        
        item.add(Box.createRigidArea(new Dimension(10, 0)));
        item.add(textLabel);
        
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                item.setBackground(AppStyles.SIDEBAR_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                item.setBackground(AppStyles.SIDEBAR_COLOR);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                cardLayout.show(contentPanel, text);
            }
        });
        
        return item;
    }

    public static class ContactListCellRenderer extends JPanel implements ListCellRenderer<ContactModel> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel phoneLabel = new JLabel();
        private final JLabel deleteLabel = new JLabel("🗑️");

        public ContactListCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            JPanel infoPanel = new JPanel(new BorderLayout());
            infoPanel.setOpaque(false);
            
            nameLabel.setFont(AppStyles.getAppFont(Font.BOLD, 14f));
            phoneLabel.setFont(AppStyles.getAppFont(Font.PLAIN, 12f));
            phoneLabel.setForeground(Color.DARK_GRAY);
            
            infoPanel.add(nameLabel, BorderLayout.NORTH);
            infoPanel.add(phoneLabel, BorderLayout.SOUTH);
            
            deleteLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            deleteLabel.setForeground(AppStyles.DARK_RED);
            
            add(infoPanel, BorderLayout.CENTER);
            add(deleteLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ContactModel> list, 
                                                    ContactModel value, 
                                                    int index,
                                                    boolean isSelected, 
                                                    boolean cellHasFocus) {
            nameLabel.setText(value.getName());
            phoneLabel.setText(value.getPhoneNumber());
            setBackground(isSelected ? new Color(230, 244, 255) : Color.WHITE);
            return this;
        }
    }
}