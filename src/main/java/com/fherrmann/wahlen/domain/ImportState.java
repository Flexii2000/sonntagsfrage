package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Kleiner Key-Value-Store fuer den Zustand des DAWUM-Imports. */
@Entity
@Table(name = "import_state")
public class ImportState {

    public static final String DAWUM_LAST_UPDATE = "dawum_last_update";
    public static final String DAWUM_ETAG = "dawum_etag";
    public static final String LAST_SUCCESSFUL_RUN = "last_successful_run";
    public static final String LAST_ERROR = "last_error";

    @Id
    @Column(name = "state_key")
    private String key;

    @Column(name = "state_value")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ImportState() {
    }

    public ImportState(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
