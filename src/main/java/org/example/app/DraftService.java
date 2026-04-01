package org.example.app;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory draft/form persistence service.
 * Allows UI screens to save partially-filled form fields so that navigating
 * away and returning restores the user's previous input.
 *
 * Data is kept only for the lifetime of the JVM process (no disk persistence).
 */
public final class DraftService {

    // key: draftId (e.g. "AUTHOR_REGISTER"), value: map of field→value
    private static final ConcurrentHashMap<String, Map<String, String>> store = new ConcurrentHashMap<>();

    private DraftService() {}

    /**
     * Persist a map of field values for the given draft id.
     * Replaces any previously stored draft with the same id.
     *
     * @param draftId  identifier for the draft (e.g. "AUTHOR_REGISTER")
     * @param fields   field-name → value pairs to store
     */
    public static void saveDraft(String draftId, Map<String, String> fields) {
        if (draftId == null || fields == null) return;
        store.put(draftId, new HashMap<>(fields));
    }

    /**
     * Load a previously saved draft.
     *
     * @param draftId  identifier for the draft
     * @return an unmodifiable copy of the stored fields, or an empty map if none exists
     */
    public static Map<String, String> loadDraft(String draftId) {
        if (draftId == null) return Collections.emptyMap();
        Map<String, String> draft = store.get(draftId);
        return draft != null ? Collections.unmodifiableMap(draft) : Collections.emptyMap();
    }

    /**
     * Remove a saved draft (call on successful form submission or explicit cancel).
     *
     * @param draftId  identifier for the draft to remove
     */
    public static void clearDraft(String draftId) {
        if (draftId != null) store.remove(draftId);
    }

    /**
     * Remove all stored drafts (e.g. on logout).
     */
    public static void clearAll() {
        store.clear();
    }
}
