package com.krisped;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@PluginDescriptor(
    name = "Item Search",
    description = "Search for items and view their actions and icons"
)
public class ItemSearchPlugin extends Plugin {
    @Inject private ClientToolbar clientToolbar;
    @Inject private ItemManager itemManager;
    @Inject private ClientThread clientThread;

    private ItemSearchPanel panel;
    private NavigationButton navButton;

    private final Map<Integer, BufferedImage> imageCache = new ConcurrentHashMap<>();

    private String lastQuery = "";
    private volatile boolean searchInProgress = false;

    @Override
    protected void startUp() {
        panel = new ItemSearchPanel(new SearchListener());
        panel.setStatus("");
        BufferedImage navIcon = placeholder();
        try {
            java.net.URL u = getClass().getResource("/com/krisped/icons/Deadman_world_icon.png");
            if (u != null) {
                navIcon = javax.imageio.ImageIO.read(u);
            }
        } catch (Exception ignored) {}
        navButton = NavigationButton.builder()
            .tooltip("Item Search")
            .icon(navIcon)
            .priority(5)
            .panel(panel)
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        imageCache.clear();
        lastQuery = "";
        searchInProgress = false;
    }

    private class SearchListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String q = panel.getSearchText().trim();
            Object src = e != null ? e.getSource() : null;
            boolean explicit = (src instanceof JTextField) || (src instanceof JButton);
            if (explicit) {
                if (q.length() < 2) {
                    lastQuery = "";
                    panel.setResults(Collections.emptyList());
                    panel.setStatus(q.isEmpty() ? "" : "Minst 2 tegn");
                    return;
                }
                lastQuery = q;
                triggerSearch(q, true);
            } else {
                if (lastQuery.length() >= 2) triggerSearch(lastQuery, false);
            }
        }
    }

    private void triggerSearch(String query, boolean fresh) {
        if (searchInProgress) return; // simple guard; could support cancellation if needed
        searchInProgress = true;
        panel.setStatus("Søker...");
        final String q = query;
        final Set<String> filters = panel.getSelectedFilters();
        final String sort = panel.getSortOption();
        clientThread.invoke(() -> {
            List<ItemSearchResult> results = performClientThreadSearch(q, filters, sort);
            SwingUtilities.invokeLater(() -> {
                panel.setResults(results);
                searchInProgress = false;
            });
        });
    }

    private List<ItemSearchResult> performClientThreadSearch(String query, Set<String> filters, String sortOption) {
        String lower = query.toLowerCase();
        List<ItemSearchResult> out = new ArrayList<>();
        List<?> raw;
        try {
            raw = itemManager.search(query);
        } catch (Exception ex) {
            return out; // return empty if search fails
        }
        if (raw == null) return out;
        int collected = 0;
        boolean needFilter = !filters.isEmpty();
        // Normalize filters (treat wield/wear/equip synonyms)
        Set<String> normFilters = new HashSet<>();
        for (String f : filters) {
            String lf = f.toLowerCase();
            if (lf.equals("wear") || lf.equals("equip")) lf = "wield"; // collapse synonyms
            normFilters.add(lf);
        }
        for (Object o : raw) {
            if (collected >= 400) break; // pre-trim
            Integer id = extractItemId(o);
            if (id == null) continue;
            ItemComposition comp;
            try { comp = itemManager.getItemComposition(id); } catch (Exception ex) { continue; }
            if (comp == null) continue;
            int canonicalId = id;
            try { canonicalId = itemManager.canonicalize(id); } catch (Throwable ignored) {}
            if (canonicalId != id) {
                try {
                    ItemComposition base = itemManager.getItemComposition(canonicalId);
                    if (base != null) comp = base; // use canonical for name/actions
                } catch (Exception ignored) {}
            }
            String name = comp.getName();
            if (name == null || name.equalsIgnoreCase("null")) continue;
            if (!name.toLowerCase().contains(lower)) continue; // extra guard if search is broad
            // Collect actions from original + canonical (avoid duplicates)
            Set<String> actionSet = new LinkedHashSet<>();
            addActions(actionSet, comp.getInventoryActions());
            if (canonicalId != id) {
                try {
                    ItemComposition orig = itemManager.getItemComposition(id);
                    if (orig != null) addActions(actionSet, orig.getInventoryActions());
                } catch (Exception ignored) {}
            }
            if (actionSet.isEmpty()) {
                // Skip utterly inert items unless no filter
                if (needFilter) continue;
            }
            // Build display actions (preserve original case best-effort)
            List<String> displayActions = new ArrayList<>(actionSet);
            // Filter match
            if (needFilter) {
                boolean match = false;
                for (String f : normFilters) {
                    for (String a : actionSet) {
                        String la = a.toLowerCase();
                        if (la.equals(f)) { match = true; break; }
                        if (f.equals("wield") && (la.equals("wear") || la.equals("equip"))) { match = true; break; }
                        if (f.equals("eat") && la.equals("consume")) { match = true; break; }
                        if (f.equals("drink") && (la.equals("quaff") || la.equals("sip"))) { match = true; break; }
                    }
                    if (match) break;
                }
                if (!match) continue;
            }
            BufferedImage img = imageCache.computeIfAbsent(id, k -> {
                try { return itemManager.getImage(k); } catch (Exception e) { return placeholder(); }
            });
            out.add(new ItemSearchResult(id, img != null ? img : placeholder(), name, displayActions));
            collected++;
        }
        applySort(out, sortOption);
        if (out.size() > 100) out.subList(100, out.size()).clear();
        return out;
    }

    private void addActions(Set<String> target, String[] arr) {
        if (arr == null) return;
        for (String a : arr) {
            if (a == null || a.isEmpty()) continue;
            target.add(a); // keep original case first occurrence
        }
    }

    private void applySort(List<ItemSearchResult> list, String sortOption) {
        if (sortOption == null) return;
        switch (sortOption) {
            case "Navn A-Z":
                list.sort(Comparator.comparing(ItemSearchResult::getName, String.CASE_INSENSITIVE_ORDER));
                break;
            case "Navn Z-A":
                list.sort(Comparator.comparing(ItemSearchResult::getName, String.CASE_INSENSITIVE_ORDER).reversed());
                break;
            case "ID ↑":
                list.sort(Comparator.comparingInt(ItemSearchResult::getId));
                break;
            case "ID ↓":
                list.sort(Comparator.comparingInt(ItemSearchResult::getId).reversed());
                break;
        }
    }

    private Integer extractItemId(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        String[] names = {"getItemId", "getItemID", "getId", "getID"};
        for (String n : names) {
            try {
                Object v = obj.getClass().getMethod(n).invoke(obj);
                if (v instanceof Integer) return (Integer) v;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private BufferedImage placeholder() {
        return new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    }
}
