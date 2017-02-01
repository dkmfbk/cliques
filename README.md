# Using the Semantic Web to Understand Persons’ Networks extracted from Text

This project is the result of a work describing a methodology to interpret large persons’ networks extracted from text by classifying cliques using the DBpedia ontology.
The approach relies on a combination of NLP, Semantic web technologies and network analysis.
The classification methodology that first starts from single nodes and then generalises to cliques is effective in terms of performance and is able to deal also with nodes that are not linked to Wikipedia.
The gold standard manually developed for evaluation shows that groups of co-occurring entities share in most of the cases a category that can be automatically assigned.
This holds for both languages considered in this study.
The outcome of this work may be of interest to enhance the readability of large networks and to provide an additional semantic layer on top of cliques.
Furthermore, it represents an unsupervised approach to automatically extend DBpedia starting from a corpus.

## Datasets

* [Cliques gold standard (English)](https://github.com/dkmfbk/cliques/blob/master/src/main/resources/cliques_nk.txt)
* [Cliques gold standard (Italian)](https://github.com/dkmfbk/cliques/blob/master/src/main/resources/cliques_adige.txt)
* [Dataset containing the original Nixon and Kennedy speech transcriptions](http://www.airpedia.org/nk-naf.zip) (released under the NARA public domain license) along with the linguistic annotations applied in the pre-processing step (in NAF format)
