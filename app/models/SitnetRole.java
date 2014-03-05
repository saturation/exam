package models;

import be.objectify.deadbolt.core.models.Role;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class SitnetRole extends Model implements Role {

    public static final Finder<Long, SitnetRole> find = new Finder<Long, SitnetRole>(Long.class, SitnetRole.class);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static SitnetRole findByName(String name)
    {
        return find.where()
                .eq("name",
                        name)
                .findUnique();
    }

}