# clj-crossref

Crossref API client library.

## Usage

In project.clj:

    [crossref/clj-crossref "0.0.1"]
	
An example:

    (use 'clj-crossref.core)
	
	(work "10.5555/12345678")
	;; => { ... work metadata ... }
	
Supports ID look up, counts, sampled queries, free-text queries, filters, paging, faceting.
