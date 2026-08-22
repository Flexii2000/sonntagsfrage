package com.fherrmann.wahlen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Erhebungsmethode (Online, Telefon, ...). ID stammt aus der DAWUM-API. */
@Entity
@Table(name = "method")
public class Method {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    protected Method() {
    }

    public Method(Integer id, String name) {
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
