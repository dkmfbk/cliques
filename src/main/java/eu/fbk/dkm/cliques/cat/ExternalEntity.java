package eu.fbk.dkm.cliques.cat;

/**
 * Created by alessio on 25/01/16.
 */

public class ExternalEntity extends Entity {

    String relatedTo;
    String tagDescriptor;
    String externalRef;
    String type;

    public ExternalEntity(Integer id, String relatedTo, String tagDescriptor, String externalRef, String type,
            String comment) {
        super(id, comment);
        this.relatedTo = relatedTo;
        this.tagDescriptor = tagDescriptor;
        this.externalRef = externalRef;
        this.type = type;
    }

    public String getRelatedTo() {
        return relatedTo;
    }

    public void setRelatedTo(String relatedTo) {
        this.relatedTo = relatedTo;
    }

    public String getTagDescriptor() {
        return tagDescriptor;
    }

    public void setTagDescriptor(String tagDescriptor) {
        this.tagDescriptor = tagDescriptor;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
