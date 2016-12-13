package eu.fbk.dkm.cliques.cat;

import org.w3c.dom.Element;

/**
 * Created by alessio on 25/01/16.
 */

public class Token {

    Integer id, sentence, number;
    String text;

    public static Token fromElement(Element element) {
        Integer id = Integer.parseInt(element.getAttribute("t_id"));
        Integer sentence = Integer.parseInt(element.getAttribute("sentence"));
        Integer number = Integer.parseInt(element.getAttribute("number"));
        String text = element.getTextContent();

        return new Token(id, sentence, number, text);
    }

    public Token(Integer id, Integer sentence, Integer number, String text) {
        this.id = id;
        this.sentence = sentence;
        this.number = number;
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSentence() {
        return sentence;
    }

    public void setSentence(Integer sentence) {
        this.sentence = sentence;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }
}
