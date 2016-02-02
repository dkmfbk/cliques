package eu.fbk.dkm.cliques.cat;

import java.util.List;
import java.util.TreeSet;

/**
 * Created by alessio on 25/01/16.
 */

public class EntityMention extends Entity {
    String subType;
    String syntacticType;
    TreeSet<Integer> tokenAnchors = new TreeSet<>();
    Integer target;

    public EntityMention(Integer id, String subType, String syntacticType, String comment) {
        super(id, comment);
        this.subType = subType;
        this.syntacticType = syntacticType;
    }

    public void addTokenAnchor(Integer tokenID) {
        tokenAnchors.add(tokenID);
    }

    public Integer getTarget() {
        return target;
    }

    public void setTarget(Integer target) {
        this.target = target;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public String getSyntacticType() {
        return syntacticType;
    }

    public void setSyntacticType(String syntacticType) {
        this.syntacticType = syntacticType;
    }

    public TreeSet<Integer> getTokenAnchors() {
        return tokenAnchors;
    }

    public void setTokenAnchors(TreeSet<Integer> tokenAnchors) {
        this.tokenAnchors = tokenAnchors;
    }
}
