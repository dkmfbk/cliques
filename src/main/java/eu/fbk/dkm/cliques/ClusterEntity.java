package eu.fbk.dkm.cliques;

import com.google.common.base.Joiner;

import java.util.*;

/**
 * Created by alessio on 10/11/16.
 */

class ClusterEntity {

    public ArrayList<String> words = new ArrayList<String>();
    public TreeSet<String> orderWords = new TreeSet<String>();
    public Set<String> fileReference = new HashSet<String>();
    public Set<String> alternativeForms = new HashSet<String>();
    public Map<String, Integer> alternativeFormsWithFrequency = new HashMap<String, Integer>();
    public Integer Frequency = 0;

    public ClusterEntity(String[] wordArray) {
        for (String s : wordArray) {
            //s = s.toLowerCase();
            if (s.length() > 0) {
                words.add(s);
                orderWords.add(s);
            }
        }
    }

    public ClusterEntity() {

    }

    private String getMostCompleteForm() {
        String out = Joiner.on(" ").join(this.words);
        for (String s : alternativeForms) {
            if (s.length() > out.length()) {
                out = s;
            }
        }
        return out;
    }

    String getMostFrequentCompositeForm() {
        int freq = 0;
        String out = "";
        if (words.size() > 1) {
            out = Joiner.on(" ").join(this.words);
            freq = this.Frequency;
        }
        for (String s : alternativeForms) {

            if (alternativeFormsWithFrequency.get(s) > freq && s.split(" ").length > 1) {
                out = s;
            }

        }
        return out;
    }

    Set<String> getAllForms() {
        Set<String> forme = new HashSet<String>();
        forme.add(this.toString());
        for (String s : alternativeForms) {
            forme.add(s);
        }
        return forme;
    }

    String getMostCompleteFormWithSyn() {
        String out = Joiner.on(" ").join(this.words);

        Set<String> forms = new HashSet<String>();
        forms.add(this.toString());
        for (String s : alternativeForms) {
            forms.add(s);
            if (s.length() > out.length()) {
                out = s;
            }
        }
        return out + "{" + Joiner.on(",").join(forms) + "}";
    }

    void addToFrequency(Integer num) {
        this.Frequency += num;
    }

    void incrementFrequency() {
        this.Frequency++;
    }

    void addFileReference(String ref) {
        this.fileReference.add(ref);
    }

    void mergeFileReference(Set ref) {
        this.fileReference.addAll(ref);
    }

    @Override
    public String toString() {
        return Joiner.on(" ").join(words);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ClusterEntity)) {
            return false;
        }
        ClusterEntity otherMyClass = (ClusterEntity) other;

        if (this.orderWords.equals(otherMyClass.orderWords)) {
            //System.out.println("Equals: " + this.orderWords.toString() + " / " + otherMyClass.orderWords.toString());
            return true;
        } else if (intersect(this.words, otherMyClass.words).size() > 1) {
            //if (intersect(new ArrayList<String>(this.fileReference), new ArrayList<String>(otherMyClass.fileReference)).size() > 0){
            //System.out.println("Intersect : " + this.toString() + " / " + otherMyClass.toString());
            if (stringOverlap(this.words, otherMyClass.words)) {
                return true;
            } else {
                return false;
            }
        } else if (intersect(this.words, otherMyClass.words).size() > 0 && (this.words.size() < 2
                || otherMyClass.words.size() < 2)) {
            //if (intersect(new ArrayList<String>(this.fileReference), new ArrayList<String>(otherMyClass.fileReference)).size() > 0){
            //System.out.println("Intersect : " + this.toString() + " / " + otherMyClass.toString());
            if (stringOverlap(this.words, otherMyClass.words)) {
                return true;
            } else {
                return false;
            }
        } else if (alternativeForms.contains(otherMyClass.toString())) {
            //if (intersect(new ArrayList<String>(this.fileReference), new ArrayList<String>(otherMyClass.fileReference)).size() > 0) {
            return true;
            //} else {
            //	return false;
            //}
        } else {
            return false;
        }

    }

    private boolean stringOverlap(ArrayList<String> a, ArrayList<String> b) {
        ArrayList<String> acopy = new ArrayList<String>();
        ArrayList<String> bcopy = new ArrayList<String>();//longest

        acopy.addAll(a);
        bcopy.addAll(b);

        int aOnB = 0;
        int bOna = 0;

        for (String match1 : bcopy) {
            if (acopy.contains(match1)) {
                acopy = new ArrayList<String>(acopy.subList(acopy.indexOf(match1), acopy.size()));
                bOna++;
            }
        }

        acopy = new ArrayList<String>();//longest
        bcopy = new ArrayList<String>();
        acopy.addAll(a);
        bcopy.addAll(b);

        for (String match1 : acopy) {
            if (bcopy.contains(match1)) {
                bcopy = new ArrayList<String>(bcopy.subList(bcopy.indexOf(match1), bcopy.size()));
                aOnB++;
            }
        }

        if (aOnB == a.size() || bOna == b.size()) {
            return true;
        } else {
            return false;
        }

    }

    private static ArrayList<String> intersect(ArrayList<String> a, ArrayList<String> b) {

        ArrayList<String> acopy = new ArrayList<String>();
        ArrayList<String> bcopy = new ArrayList<String>();
        acopy.addAll(a);
        bcopy.addAll(b);

        ArrayList<String> result = new ArrayList<String>();

        for (String t : acopy) {
            if (bcopy.remove(t)) {
                result.add(t);
            }
        }

        return result;
    }
}
