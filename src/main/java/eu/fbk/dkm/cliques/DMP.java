package eu.fbk.dkm.cliques;

import eu.fbk.utils.core.diff_match_patch;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by alessio on 16/11/16.
 */

public class DMP extends diff_match_patch {
    // CUSTOM FUNCTIONS

    /**
     * Find the mappings between two sets of tokens.
     *
     * @param strings1 First set of tokens
     * @param strings2 Second set of tokens
     * @return a Map that connects indexes from the two sets
     */
    public Map<Integer, Integer> findMapping(List<String> strings1, List<String> strings2) {
        return findMapping(strings1, strings2, false);
    }

    /**
     * Find the mappings between two sets of tokens.
     *
     * @param strings1    First set of tokens
     * @param strings2    Second set of tokens
     * @param switchOrder Order of comparison
     * @return a Map that connects indexes from the two sets
     */
    public Map<Integer, Integer> findMapping(List<String> strings1, List<String> strings2, boolean switchOrder) {
        Map<Integer, Integer> offsets1 = new HashMap<>();
        Map<Integer, Integer> offsets2 = new HashMap<>();

        String string1 = setOffsets(strings1, offsets1);
        String string2 = setOffsets(strings2, offsets2);

        LinkedList<Diff> diffs = diff_main(string1, string2, false);
        int lastIndex1 = 0, lastIndex2 = 0;
        HashMap<Integer, Integer> returnMap = new HashMap<>();

        for (DMP.Diff diff : diffs) {

            int length = diff.text.length();

            switch (diff.operation) {
            case EQUAL:
                for (int i = 0; i < length; i++) {
                    Integer i1 = offsets1.get(lastIndex1 + i);
                    Integer i2 = offsets2.get(lastIndex2 + i);
                    if (i1 != null && i2 != null) {
                        if (switchOrder) {
                            returnMap.put(i2, i1);
                        } else {
                            returnMap.put(i1, i2);
                        }
                    }
                }
                lastIndex1 += length;
                lastIndex2 += length;
                break;
            case INSERT:
                lastIndex2 += length;
                break;
            case DELETE:
                lastIndex1 += length;
                break;
            }
        }

        return returnMap;
    }

    /**
     * Set offsets and return the concat of the string list.
     *
     * @param stringList List of the strings to compare
     * @param offsets    The offsets Map, that will be modified by this method
     * @return
     */
    private static String setOffsets(List<String> stringList, Map<Integer, Integer> offsets) {
        int c = 0;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < stringList.size(); i++) {
            String s = stringList.get(i);
            offsets.put(c, i);
            buffer.append(s);
            c += s.length();
        }

        return buffer.toString();
    }

}
