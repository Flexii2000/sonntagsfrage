package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Auftraggeber einer Umfrage. ID stammt aus der DAWUM-API. */
@Entity
public class Tasker {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    protected Tasker() {
    }

    public Tasker(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
