package com.krisped;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.LinkedHashSet;
import java.util.Set;

public class ItemSearchPanel extends PluginPanel {
    private final JTextField searchField;
    private final JButton searchButton;
    private final JPanel resultsPanel;
    private final JLabel statusLabel;
    private final JCheckBox eatCb, drinkCb, wieldCb, wearCb, useCb;
    private final JComboBox<String> sortCombo;

    public ItemSearchPanel(ActionListener searchListener) {
        setLayout(new BorderLayout());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel searchRow = new JPanel(new BorderLayout(4,4));
        searchField = new JTextField();
        searchField.addActionListener(searchListener); // Enter
        searchButton = new JButton("Søk");
        searchButton.addActionListener(searchListener);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        top.add(searchRow);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        eatCb = new JCheckBox("Eat");
        drinkCb = new JCheckBox("Drink");
        wieldCb = new JCheckBox("Wield");
        wearCb = new JCheckBox("Wear");
        useCb = new JCheckBox("Use");
        filters.add(eatCb); filters.add(drinkCb); filters.add(wieldCb); filters.add(wearCb); filters.add(useCb);
        top.add(filters);

        for (JCheckBox cb : new JCheckBox[]{eatCb, drinkCb, wieldCb, wearCb, useCb}) {
            cb.addActionListener(e -> searchListener.actionPerformed(null)); // re-run last search with filters
        }

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sortCombo = new JComboBox<>(new String[]{"Navn A-Z","Navn Z-A","ID ↑","ID ↓"});
        sortCombo.addActionListener(e -> searchListener.actionPerformed(null));
        sortPanel.add(new JLabel("Sort:"));
        sortPanel.add(sortCombo);
        top.add(sortPanel);

        add(top, BorderLayout.NORTH);

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(resultsPanel);
        add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel("");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        add(statusLabel, BorderLayout.SOUTH);
    }

    public String getSearchText() { return searchField.getText(); }
    public void setStatus(String text) { statusLabel.setText(text == null ? "" : text); }

    public Set<String> getSelectedFilters() {
        Set<String> s = new LinkedHashSet<>();
        if (eatCb.isSelected()) s.add("eat");
        if (drinkCb.isSelected()) s.add("drink");
        if (wieldCb.isSelected()) s.add("wield");
        if (wearCb.isSelected()) s.add("wear");
        if (useCb.isSelected()) s.add("use");
        return s;
    }

    public String getSortOption() { return (String) sortCombo.getSelectedItem(); }

    public void setResults(java.util.List<ItemSearchResult> results) {
        resultsPanel.removeAll();
        if (results.isEmpty()) { setStatus("Ingen treff"); } else { setStatus(results.size() + " treff"); }
        for (ItemSearchResult result : results) {
            JPanel itemPanel = new JPanel(new BorderLayout());
            JLabel imageLabel = new JLabel(new ImageIcon(result.getImage()));
            itemPanel.add(imageLabel, BorderLayout.WEST);
            JLabel nameLabel = new JLabel(result.getName() + " (" + result.getId() + ")");
            itemPanel.add(nameLabel, BorderLayout.CENTER);
            JLabel actionsLabel = new JLabel(String.join(", ", result.getActions()));
            itemPanel.add(actionsLabel, BorderLayout.EAST);
            resultsPanel.add(itemPanel);
        }
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }
}
