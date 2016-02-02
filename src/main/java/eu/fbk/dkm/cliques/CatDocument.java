package eu.fbk.dkm.cliques;

import eu.fbk.dkm.cliques.cat.Entity;
import eu.fbk.dkm.cliques.cat.EntityMention;
import eu.fbk.dkm.cliques.cat.ExternalEntity;
import eu.fbk.dkm.cliques.cat.Token;
import org.joox.Match;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.joox.JOOX.$;

/**
 * Created by alessio on 25/01/16.
 */

public class CatDocument {

    private Map<Integer, Token> tokens = new TreeMap<>();
    private Map<Integer, Entity> entities = new TreeMap<>();
    private Map<Integer, Integer> relations = new HashMap<>();
    private String name;

    private CatDocument() {

    }

    public String getName() {
        return name;
    }

    public Map<Integer, Token> getTokens() {
        return tokens;
    }

    public Map<Integer, Entity> getEntities() {
        return entities;
    }

    public Map<Integer, Integer> getRelations() {
        return relations;
    }

    static public CatDocument readFromFile(File file) throws Exception {
        Document document = $(file).document();
        CatDocument ret = new CatDocument();

        // NAME
        ret.name = $(document).get(0).getAttribute("doc_name");

        // TOKENS
        Match tokens = $(document).find("token");
        for (Element token : tokens) {
            Token t = Token.fromElement(token);
            ret.tokens.put(t.getId(), t);
        }

        // RELATIONS
        Match references = $(document).find("REFERS_TO");
        for (Element reference : references) {
            Match targets = $(reference).find("target");
            if (targets.size() == 1) {
                Element target = targets.get(0);
                Integer targetID = Integer.parseInt(target.getAttribute("m_id"));

                Match sources = $(reference).find("source");
                for (Element source : sources) {
                    int sourceID = Integer.parseInt(source.getAttribute("m_id"));
                    ret.relations.put(sourceID, targetID);
                }
            } else {
                if (targets.size() > 0) {
                    throw new Exception("Target is not unique");
                }
            }
        }

        // ENTITIES
        Match entityMentions = $(document).find("ENTITY_MENTION");
        for (Element entityMention : entityMentions) {
            Integer id = Integer.parseInt(entityMention.getAttribute("m_id"));
            String subType = entityMention.getAttribute("Subtype");
            String comment = entityMention.getAttribute("Comment");
            String syntacticType = entityMention.getAttribute("Syntactic_type");
            EntityMention entity = new EntityMention(id, subType, syntacticType, comment);
            Integer target = ret.relations.get(id);
            if (target != null) {
                entity.setTarget(target);
            }

            Match tokenAnchors = $(entityMention).find("token_anchor");
            for (Element tokenAnchor : tokenAnchors) {
                Integer tokenID = Integer.parseInt(tokenAnchor.getAttribute("t_id"));
                entity.addTokenAnchor(tokenID);
            }

            ret.entities.put(entity.getId(), entity);
        }

        Match externalEntities = $(document).find("ENTITY");
        for (Element externalEntity : externalEntities) {
            Integer id = Integer.parseInt(externalEntity.getAttribute("m_id"));
            String comment = externalEntity.getAttribute("Comment");
            String type = externalEntity.getAttribute("Type");
            String externalRef = externalEntity.getAttribute("External_ref");
            String tagDescriptor = externalEntity.getAttribute("TAG_DESCRIPTOR");
            String relatedTo = externalEntity.getAttribute("RELATED_TO");

            ExternalEntity entity = new ExternalEntity(id, relatedTo, tagDescriptor, externalRef, type, comment);
            ret.entities.put(entity.getId(), entity);
        }

        return ret;
    }
}
