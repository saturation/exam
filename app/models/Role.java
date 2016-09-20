package models;


import models.base.GeneratedIdentityModel;

import javax.persistence.Entity;

@Entity
public class Role extends GeneratedIdentityModel implements be.objectify.deadbolt.java.models.Role {

    public enum Name {STUDENT, TEACHER, ADMIN}

    private String name;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        Role role = (Role) o;
        return !(name != null ? !name.equals(role.name) : role.name != null);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
