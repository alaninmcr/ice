package org.jbei.ice.lib.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.jbei.ice.controllers.ControllerFactory;
import org.jbei.ice.controllers.common.ControllerException;
import org.jbei.ice.lib.account.model.Account;
import org.jbei.ice.lib.dao.hibernate.HibernateHelper;
import org.jbei.ice.lib.entry.attachment.Attachment;
import org.jbei.ice.lib.entry.model.Entry;
import org.jbei.ice.lib.entry.sample.model.Sample;
import org.jbei.ice.lib.logging.Logger;
import org.jbei.ice.lib.models.Sequence;
import org.jbei.ice.lib.search.blast.BlastException;
import org.jbei.ice.lib.search.blast.BlastPlus;
import org.jbei.ice.lib.search.filter.SearchFieldFactory;
import org.jbei.ice.lib.shared.BioSafetyOption;
import org.jbei.ice.lib.shared.ColumnField;
import org.jbei.ice.lib.shared.dto.AccountType;
import org.jbei.ice.lib.shared.dto.Visibility;
import org.jbei.ice.lib.shared.dto.entry.EntryType;
import org.jbei.ice.lib.shared.dto.entry.PartData;
import org.jbei.ice.lib.shared.dto.search.SearchQuery;
import org.jbei.ice.lib.shared.dto.search.SearchResultInfo;
import org.jbei.ice.lib.shared.dto.search.SearchResults;
import org.jbei.ice.server.ModelToInfoFactory;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.hibernate.Session;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.dsl.TermContext;

/**
 * Apache Lucene full text library functionality in Hibernate
 *
 * @author Hector Plahar
 */
@SuppressWarnings("unchecked")
public class HibernateSearch {

    private HibernateSearch() {
    }

    private static class SingletonHolder {
        private static final HibernateSearch INSTANCE = new HibernateSearch();
    }

    /**
     * Retrieve the singleton instance of this class.
     *
     * @return HibernateSearch instance.
     */
    public static HibernateSearch getInstance() {
        return SingletonHolder.INSTANCE;
    }

    protected BooleanQuery generateQueriesForType(FullTextSession fullTextSession, ArrayList<EntryType> entryTypes,
            BooleanQuery booleanQuery, String term, BioSafetyOption option,
            HashMap<String, Float> userBoost) {
        term = cleanQuery(term);
        for (EntryType type : entryTypes) {
            Class<?> clazz = SearchFieldFactory.entryClass(type);

            QueryBuilder qb = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(clazz).get();
            if (term != null && !term.isEmpty()) {
                HashSet<String> commonFields = SearchFieldFactory.getCommonFields();
                commonFields.addAll(SearchFieldFactory.entryFields(type));
                for (String field : commonFields) {
                    // TODO ignoreFieldBridges only for enums only (e.g. plantType, generation) etc
                    Query fieldQuery = qb.keyword().fuzzy().withThreshold(0.8f)
                                         .onField(field).ignoreFieldBridge().matching(term).createQuery();
                    Float boost = userBoost.get(field);
                    if (boost != null)
                        fieldQuery.setBoost(boost);
                    booleanQuery.add(fieldQuery, BooleanClause.Occur.SHOULD);
                }
            }

            // pending visibility
            Query visibilityQuery = qb.keyword().onField("visibility")
                                      .matching(Visibility.PENDING.getValue()).createQuery();
            booleanQuery.add(visibilityQuery, BooleanClause.Occur.MUST_NOT);

            // draft visibility
            Query visibilityDraftQuery = qb.keyword().onField("visibility")
                                           .matching(Visibility.DRAFT.getValue()).createQuery();
            booleanQuery.add(visibilityDraftQuery, BooleanClause.Occur.MUST_NOT);

            // exclude deleted
            Query deletedQuery = qb.keyword().onField("ownerEmail").matching("system").createQuery();
            booleanQuery.add(deletedQuery, BooleanClause.Occur.MUST_NOT);

            // biosafety
            if (option != null) {
                TermContext levelContext = qb.keyword();
                org.apache.lucene.search.Query biosafetyQuery =
                        levelContext.onField("bioSafetyLevel").ignoreFieldBridge()
                                    .matching(option.getValue()).createQuery();
                booleanQuery.add(biosafetyQuery, BooleanClause.Occur.MUST);
            }
        }
        return booleanQuery;
    }

    public SearchResults executeSearchNoTerms(Account account, SearchQuery searchQuery,
            String projectName, String projectURL) {
        ArrayList<EntryType> entryTypes = searchQuery.getEntryTypes();
        if (entryTypes == null) {
            entryTypes = new ArrayList<>();
            entryTypes.addAll(Arrays.asList(EntryType.values()));
        }

        Session session = HibernateHelper.getSessionFactory().getCurrentSession();
        int resultCount;
        FullTextSession fullTextSession = Search.getFullTextSession(session);
        BooleanQuery booleanQuery = new BooleanQuery();
        // get classes for search
//        Class<?>[] classes = new Class<?>[EntryType.values().length];
//        for (int i = 0; i < entryTypes.size(); i += 1) {
//            classes[i] = SearchFieldFactory.entryClass(entryTypes.get(i));
//        }

        run(session, "entry.canRead", Sequence.class);

        for (EntryType type : EntryType.values()) {
            Class<?> clazz = SearchFieldFactory.entryClass(type);

            QueryBuilder qb = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(clazz).get();

            // pending visibility
            Query visibilityQuery = qb.keyword().onField("visibility")
                                      .matching(Visibility.PENDING.getValue()).createQuery();
            booleanQuery.add(visibilityQuery, BooleanClause.Occur.MUST_NOT);

            // draft visibility
            Query visibilityDraftQuery = qb.keyword().onField("visibility")
                                           .matching(Visibility.DRAFT.getValue()).createQuery();
            booleanQuery.add(visibilityDraftQuery, BooleanClause.Occur.MUST_NOT);

            // exclude deleted
            Query deletedQuery = qb.keyword().onField("ownerEmail").matching("system").createQuery();
            booleanQuery.add(deletedQuery, BooleanClause.Occur.MUST_NOT);
        }

//        booleanQuery = generateQueriesForType(fullTextSession, entryTypes, booleanQuery, null,
//                searchQuery.getBioSafetyOption(), userBoost);

        // wrap Lucene query in a org.hibernate.Query
        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(booleanQuery);

        // get max score
        fullTextQuery.setFirstResult(0); //start from the startth element
        fullTextQuery.setMaxResults(1);  //return count elements
        fullTextQuery.setProjection(FullTextQuery.SCORE);
        List result = fullTextQuery.list();
        float maxScore = -1f;
        if (result.size() == 1) {
            maxScore = (Float) ((Object[]) (result.get(0)))[0];
        }
        // end get max score

        // get sorting values
        Sort sort = getSort(searchQuery.getParameters().isSortAscending(), searchQuery.getParameters().getSortField());
        fullTextQuery.setSort(sort);

        // projection (specified properties must be stored in the index @Field(store=Store.YES))
        fullTextQuery.setProjection(FullTextQuery.SCORE, FullTextQuery.THIS);

        // enable security filter if needed
        checkEnableSecurityFilter(account, fullTextQuery);

        // enable has attachment/sequence/sample (if needed)
        fullTextQuery = checkEnableHasAttribute(session, fullTextQuery, searchQuery);

        // check if there is also a blast search
        HashMap<String, SearchResultInfo> blastInfo;
        try {
            blastInfo = checkEnableBlast(account, fullTextQuery, searchQuery);
        } catch (BlastException e) {
            Logger.error(e);
            return null;
        }

        // set paging params
        fullTextQuery.setFirstResult(searchQuery.getParameters().getStart());
        fullTextQuery.setMaxResults(searchQuery.getParameters().getRetrieveCount());

        resultCount = fullTextQuery.getResultSize();
        result = fullTextQuery.list();

        LinkedList<SearchResultInfo> searchResultInfos = new LinkedList<>();
        Iterator<Object[]> iterator = result.iterator();
        while (iterator.hasNext()) {
            Object[] objects = iterator.next();
            float score = (Float) objects[0];
            Entry entry = (Entry) objects[1];
            SearchResultInfo searchResult;
            if (blastInfo != null) {
                searchResult = blastInfo.get(Long.toString(entry.getId()));
                if (searchResult == null) // this should not really happen since we already filter
                    continue;
            } else {
                searchResult = new SearchResultInfo();
                searchResult.setScore(score);
                PartData info = ModelToInfoFactory.createTableViewData(entry, true);
                searchResult.setEntryInfo(info);
            }

            searchResult.setMaxScore(maxScore);
            searchResult.setWebPartnerName(projectName);
            searchResult.setWebPartnerURL(projectURL);
            searchResultInfos.add(searchResult);
        }

        SearchResults results = new SearchResults();
        results.setResultCount(resultCount);
        results.setResults(searchResultInfos);
        String email = "Anon";
        if (account != null)
            email = account.getEmail();
        Logger.info(email + ": obtained " + resultCount + " results for \"" + searchQuery.getQueryString() + "\"");
        return results;
    }

    public SearchResults runSearchFilter(Account account, HashMap<String, SearchResultInfo> blastResults) {
        Session session = HibernateHelper.getSessionFactory().getCurrentSession();
        FullTextSession fullTextSession = Search.getFullTextSession(session);

        Class<?>[] classes = new Class<?>[EntryType.values().length];
        int i = 0;
        for (EntryType type : EntryType.values()) {
            classes[i] = SearchFieldFactory.entryClass(type);
            i += 1;
        }

        BooleanQuery booleanQuery = new BooleanQuery();

        for (EntryType type : EntryType.values()) {
            QueryBuilder qb = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(
                    SearchFieldFactory.entryClass(type)).get();
            org.apache.lucene.search.Query query = qb.keyword().onField("recordType").ignoreFieldBridge()
                                                     .matching(type.getName().toLowerCase()).createQuery();

            booleanQuery.add(query, BooleanClause.Occur.SHOULD);

            // pending visibility
            org.apache.lucene.search.Query visibilityQuery = qb.keyword()
                                                               .onField("visibility")
                                                               .ignoreFieldBridge()
                                                               .matching(Visibility.PENDING.getValue())
                                                               .createQuery();
            booleanQuery.add(visibilityQuery, BooleanClause.Occur.MUST_NOT);

            // draft visibility
            org.apache.lucene.search.Query visibilityDraftQuery = qb.keyword()
                                                                    .onField("visibility")
                                                                    .ignoreFieldBridge()
                                                                    .matching(Visibility.DRAFT.getValue())
                                                                    .createQuery();
            booleanQuery.add(visibilityDraftQuery, BooleanClause.Occur.MUST_NOT);
        }

        // wrap Lucene query in a org.hibernate.Query
        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(booleanQuery, classes);

        fullTextQuery.enableFullTextFilter("blastFilter").setParameter("recordIds", new HashSet<>(
                blastResults.keySet()));

        Set<String> groupUUIDs = new HashSet<>();
        try {
            groupUUIDs = ControllerFactory.getGroupController().retrieveAccountGroupUUIDs(account);
        } catch (ControllerException e) {
            Logger.error(e);
        }

        String email = account == null ? "" : account.getEmail();
        fullTextQuery.enableFullTextFilter("security")
                     .setParameter("account", email)
                     .setParameter("groupUUids", groupUUIDs);

        // execute search
        List result = fullTextQuery.list();
        LinkedList<SearchResultInfo> filtered = new LinkedList<>();

        for (Object object : result) {
            Entry entry = (Entry) object;
            SearchResultInfo info = blastResults.get(entry.getId() + "");
            if (info == null)
                continue;
            filtered.add(info);
        }

        SearchResults results = new SearchResults();
        results.setResultCount(filtered.size());
        results.setResults(filtered);
        return results;
    }

    public SearchResults executeSearch(Account account, Iterator<String> terms, SearchQuery searchQuery,
            String projectName, String projectURL, HashMap<String, Float> userBoost) {
        // types for which we are searching
        ArrayList<EntryType> entryTypes = searchQuery.getEntryTypes();
        if (entryTypes == null) {
            entryTypes = new ArrayList<>();
            entryTypes.addAll(Arrays.asList(EntryType.values()));
        }

        Session session = HibernateHelper.getSessionFactory().getCurrentSession();
        int resultCount;
        FullTextSession fullTextSession = Search.getFullTextSession(session);
        BooleanQuery booleanQuery = new BooleanQuery();

        // get classes for search
        Class<?>[] classes = new Class<?>[EntryType.values().length];
        for (int i = 0; i < entryTypes.size(); i += 1) {
            classes[i] = SearchFieldFactory.entryClass(entryTypes.get(i));
        }

        // generate queries for terms
        while (terms.hasNext()) {
            String term = terms.next();
            if (StandardAnalyzer.STOP_WORDS_SET.contains(term))
                continue;

            BioSafetyOption safetyOption = searchQuery.getBioSafetyOption();
            booleanQuery = generateQueriesForType(fullTextSession, entryTypes, booleanQuery, term,
                                                  safetyOption, userBoost);
        }

        if (booleanQuery.getClauses().length == 0)
            return executeSearchNoTerms(account, searchQuery, projectName, projectURL);

        // wrap Lucene query in a org.hibernate.Query
        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(booleanQuery, classes);

        // get max score
        fullTextQuery.setFirstResult(0); //start from the "start"th element
        fullTextQuery.setMaxResults(1);  //return count elements
        fullTextQuery.setProjection(FullTextQuery.SCORE);
        List result = fullTextQuery.list();
        float maxScore = -1f;
        if (result.size() == 1) {
            maxScore = (Float) ((Object[]) (result.get(0)))[0];
        }
        // end get max score

        // get sorting values
        Sort sort = getSort(searchQuery.getParameters().isSortAscending(), searchQuery.getParameters().getSortField());
        fullTextQuery.setSort(sort);

        // projection (specified properties must be stored in the index @Field(store=Store.YES))
        fullTextQuery.setProjection(FullTextQuery.SCORE, FullTextQuery.THIS);

        // enable security filter if needed
        checkEnableSecurityFilter(account, fullTextQuery);

        // enable has attachment/sequence/sample (if needed)
        fullTextQuery = checkEnableHasAttribute(session, fullTextQuery, searchQuery);

        // check if there is also a blast search
        HashMap<String, SearchResultInfo> blastInfo;
        try {
            blastInfo = checkEnableBlast(account, fullTextQuery, searchQuery);
        } catch (BlastException e) {
            Logger.error(e);
            return null;
        }

        // set paging params
        fullTextQuery.setFirstResult(searchQuery.getParameters().getStart());
        fullTextQuery.setMaxResults(searchQuery.getParameters().getRetrieveCount());

        resultCount = fullTextQuery.getResultSize();

        // execute search
        result = fullTextQuery.list();
        String email = "Anon";
        if (account != null)
            email = account.getEmail();
        Logger.info(email + ": obtained " + resultCount + " results for \"" + searchQuery.getQueryString() + "\"");

        LinkedList<SearchResultInfo> searchResultInfos = new LinkedList<>();
        Iterator<Object[]> iterator = result.iterator();
        while (iterator.hasNext()) {
            Object[] objects = iterator.next();
            float score = (Float) objects[0];
            Entry entry = (Entry) objects[1];
            SearchResultInfo searchResult;
            if (blastInfo != null) {
                searchResult = blastInfo.get(Long.toString(entry.getId()));
                if (searchResult == null) // this should not really happen since we already filter
                    continue;
            } else {
                searchResult = new SearchResultInfo();
                searchResult.setScore(score);
                PartData info = ModelToInfoFactory.createTableViewData(entry, true);
                if (info == null)
                    continue;
                searchResult.setEntryInfo(info);
            }

            searchResult.setMaxScore(maxScore);
            searchResult.setWebPartnerName(projectName);
            searchResult.setWebPartnerURL(projectURL);
            searchResultInfos.add(searchResult);
        }

        SearchResults results = new SearchResults();
        results.setResultCount(resultCount);
        results.setResults(searchResultInfos);
        return results;
    }

    protected Sort getSort(boolean asc, ColumnField sortField) {
        switch (sortField) {
            case RELEVANCE:
            default:
                return new Sort(new SortField(SortField.FIELD_SCORE.getField(), SortField.FIELD_SCORE.getType(), asc));

            case TYPE:
                return new Sort(new SortField("recordType", SortField.STRING, asc));

            case CREATED:
                return new Sort(new SortField("creationTime", SortField.STRING, asc));
        }
    }

    /**
     * Enables the security filter if the account does not have administrative privileges
     *
     * @param account       account which is checked for administrative privs
     * @param fullTextQuery search fulltextquery for which filter is enabled
     */
    protected void checkEnableSecurityFilter(Account account, org.hibernate.search.FullTextQuery fullTextQuery) {
        if (account != null && account.getType() == AccountType.ADMIN) {
            return;
        }

        Set<String> groupUUIDs = new HashSet<>();
        try {
            groupUUIDs = ControllerFactory.getGroupController().retrieveAccountGroupUUIDs(account);
        } catch (ControllerException e) {
            Logger.error(e);
        }
        String email = account == null ? "" : account.getEmail();
        fullTextQuery.enableFullTextFilter("security")
                     .setParameter("account", email)
                     .setParameter("groupUUids", groupUUIDs);
    }

    protected HashMap<String, SearchResultInfo> checkEnableBlast(Account account, FullTextQuery fullTextQuery,
            SearchQuery query) throws BlastException {
        if (!query.hasBlastQuery())
            return null;

        BlastPlus blast = new BlastPlus();
        HashMap<String, SearchResultInfo> rids = blast.runBlast(account, query.getBlastQuery());
        fullTextQuery.enableFullTextFilter("blastFilter").setParameter("recordIds", new HashSet<>(rids.keySet()));
        return rids;
    }

    protected org.hibernate.search.FullTextQuery checkEnableHasAttribute(Session session, FullTextQuery fullTextQuery,
            SearchQuery query) {
        HashSet<String> results = null;
        SearchQuery.Parameters parameters = query.getParameters();
        boolean hasSequence = parameters.getHasSequence() != null && parameters.getHasSequence();
        boolean hasAttachment = parameters.getHasAttachment() != null && parameters.getHasAttachment();
        boolean hasSample = parameters.getHasSample() != null && parameters.getHasSample();

        if (!hasSequence && !hasSample && !hasAttachment)
            return fullTextQuery;

        if (hasSequence)
            results = run(session, "hasSequence", Sequence.class);

        if (hasSample) {
            if (results != null)
                results.retainAll(run(session, "hasSample", Sample.class));
            else
                results = run(session, "hasSample", Sample.class);
        }

        if (hasAttachment) {
            if (results != null)
                results.retainAll(run(session, "hasAttachment", Attachment.class));
            else
                results = run(session, "hasAttachment", Attachment.class);
        }

        if (results != null) {
            fullTextQuery.enableFullTextFilter("boolean").setParameter("recordIds", results);
        }
        return fullTextQuery;
    }

    protected HashSet<String> run(Session session, String field, Class clazz) {
        FullTextSession fullTextSession = Search.getFullTextSession(session);

        QueryBuilder qb = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(clazz).get();
        TermContext termContext = qb.keyword();
        org.apache.lucene.search.Query query = termContext.onField(field).ignoreFieldBridge().matching("true")
                                                          .createQuery();
        BooleanQuery booleanQuery = new BooleanQuery();
        booleanQuery.add(query, BooleanClause.Occur.MUST);

        // pending visibility
        org.apache.lucene.search.Query visibilityQuery = qb.keyword()
                                                           .onField("entry.visibility")
                                                           .ignoreFieldBridge()
                                                           .matching(Visibility.PENDING.getValue())
                                                           .createQuery();
        booleanQuery.add(visibilityQuery, BooleanClause.Occur.MUST_NOT);

        // draft visibility
        org.apache.lucene.search.Query visibilityDraftQuery = qb.keyword()
                                                                .onField("entry.visibility")
                                                                .ignoreFieldBridge()
                                                                .matching(Visibility.DRAFT.getValue())
                                                                .createQuery();
        booleanQuery.add(visibilityDraftQuery, BooleanClause.Occur.MUST_NOT);

        // wrap Lucene query in a org.hibernate.Query
        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(booleanQuery, clazz);

        // execute search
        List result = fullTextQuery.list();
        Iterator<Object> iterator = result.iterator();
        HashSet<String> recordIds = new HashSet<>();
        while (iterator.hasNext()) {
            Attachment attachment = (Attachment) iterator.next();
            if (attachment.getEntry() == null)
                continue;

            recordIds.add(attachment.getEntry().getRecordId());
        }
        return recordIds;
    }

    protected static String cleanQuery(String query) {
        if (query == null)
            return query;
        String cleanedQuery = query;
        cleanedQuery = cleanedQuery.replace(":", " ");
        cleanedQuery = cleanedQuery.replace(";", " ");
        cleanedQuery = cleanedQuery.replace("\\", " ");
        cleanedQuery = cleanedQuery.replace("[", "\\[");
        cleanedQuery = cleanedQuery.replace("]", "\\]");
        cleanedQuery = cleanedQuery.replace("{", "\\{");
        cleanedQuery = cleanedQuery.replace("}", "\\}");
        cleanedQuery = cleanedQuery.replace("(", "\\(");
        cleanedQuery = cleanedQuery.replace(")", "\\)");
        cleanedQuery = cleanedQuery.replace("+", "\\+");
        cleanedQuery = cleanedQuery.replace("-", "\\-");
        cleanedQuery = cleanedQuery.replace("'", "\\'");
        cleanedQuery = cleanedQuery.replace("\"", "\\\"");
        cleanedQuery = cleanedQuery.replace("^", "\\^");
        cleanedQuery = cleanedQuery.replace("&", "\\&");

        cleanedQuery = cleanedQuery.endsWith("'") ? cleanedQuery.substring(0, cleanedQuery.length() - 1) : cleanedQuery;
        cleanedQuery = (cleanedQuery.endsWith("\\") ? cleanedQuery.substring(0,
                                                                             cleanedQuery.length() - 1) : cleanedQuery);
        if (cleanedQuery.startsWith("*") || cleanedQuery.startsWith("?")) {
            cleanedQuery = cleanedQuery.substring(1);
        }
        return cleanedQuery;
    }
}
