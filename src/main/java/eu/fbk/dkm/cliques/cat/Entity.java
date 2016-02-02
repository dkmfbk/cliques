package eu.fbk.dkm.cliques.cat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by alessio on 25/01/16.
 */

abstract public class Entity {

    String comment;
    Integer id;

    public Entity(Integer id, String comment) {
        this.comment = comment;
        this.id = id;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entity)) {
            return false;
        }

        Entity entity = (Entity) o;

        return id != null ? id.equals(entity.id) : entity.id == null;

    }
}
