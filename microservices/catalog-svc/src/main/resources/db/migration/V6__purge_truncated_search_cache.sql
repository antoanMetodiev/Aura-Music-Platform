-- Search lists are now always fetched in whole provider pages (20 ids), so a cached list that is not
-- a multiple of 20 means "the provider had no more". Rows written before that rule could have been
-- cut short (e.g. the 6 the "All" tab asks for) and would be served short forever — drop them; the
-- entities themselves stay, only the id lists are refetched on the next search.

DELETE FROM catalog.search_results
WHERE cardinality(entity_ids) % 20 <> 0;
