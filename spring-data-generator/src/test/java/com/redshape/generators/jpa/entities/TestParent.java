package com.redshape.generators.jpa.entities;

import javax.persistence.Entity;

/**
 * Created by cyril on 8/28/13.
 */
@Entity
public class TestParent {

    @javax.persistence.Id
    Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
