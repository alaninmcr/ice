package org.jbei.ice.lib.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.jbei.ice.controllers.AccountController;
import org.jbei.ice.lib.dao.DAO;
import org.jbei.ice.lib.dao.DAOException;
import org.jbei.ice.lib.logging.Logger;
import org.jbei.ice.lib.managers.AccountManager;
import org.jbei.ice.lib.managers.ConfigurationManager;
import org.jbei.ice.lib.managers.EntryManager;
import org.jbei.ice.lib.managers.GroupManager;
import org.jbei.ice.lib.managers.ManagerException;
import org.jbei.ice.lib.managers.StorageManager;
import org.jbei.ice.lib.models.Account;
import org.jbei.ice.lib.models.AccountFundingSource;
import org.jbei.ice.lib.models.Configuration;
import org.jbei.ice.lib.models.Configuration.ConfigurationKey;
import org.jbei.ice.lib.models.Entry;
import org.jbei.ice.lib.models.EntryFundingSource;
import org.jbei.ice.lib.models.FundingSource;
import org.jbei.ice.lib.models.Group;
import org.jbei.ice.lib.models.Moderator;
import org.jbei.ice.lib.models.Storage;
import org.jbei.ice.lib.models.Storage.StorageType;
import org.jbei.ice.lib.models.StorageScheme;
import org.jbei.ice.lib.permissions.PermissionManager;

public class PopulateInitialDatabase {
    public static final String DEFAULT_PLASMID_STORAGE_SCHEME_NAME = "Plasmid Storage";
    public static final String DEFAULT_STRAIN_STORAGE_SCHEME_NAME = "Strain Storage";
    public static final String DEFAULT_PART_STORAGE_SCHEME_NAME = "Part Storage";
    public static final String DEFAULT_ARABIDOPSIS_STORAGE_SCHEME_NAME = "Arabidopsis Storage";

    // This is a global "everyone" uuid
    public static String everyoneGroup = "8746a64b-abd5-4838-a332-02c356bbeac0";

    // This is the system account: "system" as the email, and "System" as last name
    public static String systemAccountEmail = "system";
    public static String adminAccountEmail = "Administrator";
    public static String adminAccountDefaultPassword = "Administrator";

    public static void main(String[] args) {
        /*
        createFirstGroup();
        populatePermissionReadGroup();
         */
        try {
            normalizeAllFundingSources();
        } catch (DAOException e) {
            e.printStackTrace();
        }
    }

    public static void initializeDatabase() throws UtilityException {
        Group group1 = null;
        try {
            group1 = GroupManager.get(everyoneGroup);
        } catch (ManagerException e) {
            throw new UtilityException(e);
        }

        if (group1 == null) {
            // Since everyone group doesn't exist, assume database is new
            // Put all other db initialization below.
            createFirstGroup();
        }

        createSystemAccount();
        createAdminAccount();

        populateDefaultStorageLocationsAndSchemes();
    }

    /**
     * Create default root node Storage for each part types
     */
    private static void populateDefaultStorageLocationsAndSchemes() throws UtilityException {
        Configuration strainRootConfig = null;
        Configuration plasmidRootConfig = null;
        Configuration partRootConfig = null;
        Configuration arabidopsisRootConfig = null;
        Storage strainRoot = null;
        Storage plasmidRoot = null;
        Storage partRoot = null;
        Storage arabidopsisSeedRoot = null;

        try {
            // read configuration
            strainRootConfig = ConfigurationManager
                    .get(ConfigurationKey.DEFAULT_STRAIN_STORAGE_HEAD);
            plasmidRootConfig = ConfigurationManager
                    .get(ConfigurationKey.DEFAULT_PLASMID_STORAGE_HEAD);
            partRootConfig = ConfigurationManager.get(ConfigurationKey.DEFAULT_PART_STORAGE_HEAD);
            arabidopsisRootConfig = ConfigurationManager
                    .get(ConfigurationKey.DEFAULT_ARABIDOPSIS_STORAGE_HEAD);
        } catch (ManagerException e1) {
            throw new UtilityException(e1);
        }

        // if null, create new storage and config
        try {
            if (strainRootConfig == null) {
                strainRoot = new Storage(DEFAULT_STRAIN_STORAGE_SCHEME_NAME,
                        "Default Strain Storage Root", StorageType.GENERIC, systemAccountEmail,
                        null);
                strainRoot = StorageManager.save(strainRoot);
                strainRootConfig = new Configuration(ConfigurationKey.DEFAULT_STRAIN_STORAGE_HEAD,
                        strainRoot.getUuid());
                ConfigurationManager.save(strainRootConfig);
            }
            if (plasmidRootConfig == null) {
                plasmidRoot = new Storage(DEFAULT_PLASMID_STORAGE_SCHEME_NAME,
                        "Default Plasmid Storage Root", StorageType.GENERIC, systemAccountEmail,
                        null);
                plasmidRoot = StorageManager.save(plasmidRoot);
                plasmidRootConfig = new Configuration(
                        ConfigurationKey.DEFAULT_PLASMID_STORAGE_HEAD, plasmidRoot.getUuid());
                ConfigurationManager.save(plasmidRootConfig);
            }
            if (partRootConfig == null) {
                partRoot = new Storage(DEFAULT_PART_STORAGE_SCHEME_NAME,
                        "Default Part Storage Root", StorageType.GENERIC, systemAccountEmail, null);
                partRoot = StorageManager.save(partRoot);
                partRoot = StorageManager.save(partRoot);
                partRootConfig = new Configuration(ConfigurationKey.DEFAULT_PART_STORAGE_HEAD,
                        partRoot.getUuid());
                ConfigurationManager.save(partRootConfig);
            }
            if (arabidopsisRootConfig == null) {
                arabidopsisSeedRoot = new Storage(DEFAULT_ARABIDOPSIS_STORAGE_SCHEME_NAME,
                        "Default Arabidopsis Seed Storage Root", StorageType.GENERIC,
                        systemAccountEmail, null);
                arabidopsisSeedRoot = StorageManager.save(arabidopsisSeedRoot);
                arabidopsisRootConfig = new Configuration(
                        ConfigurationKey.DEFAULT_ARABIDOPSIS_STORAGE_HEAD,
                        arabidopsisSeedRoot.getUuid());
                ConfigurationManager.save(arabidopsisRootConfig);
            }
        } catch (ManagerException e) {
            throw new UtilityException(e);
        }

        // create a sample storage scheme for each type
        // plasmid
        ArrayList<Storage> schemes = new ArrayList<Storage>();
        StorageScheme plasmidScheme = null;
        try {
            plasmidScheme = StorageManager.getStorageScheme(DEFAULT_PLASMID_STORAGE_SCHEME_NAME);
        } catch (ManagerException e1) {
            throw new UtilityException(e1);
        }
        if (plasmidScheme == null) {
            plasmidScheme = new StorageScheme();
            plasmidScheme.setLabel(DEFAULT_PLASMID_STORAGE_SCHEME_NAME);
            plasmidScheme.setRoot(plasmidRoot);
            schemes.add(new Storage("Freezer", "", StorageType.FREEZER, "", null));
            schemes.add(new Storage("Shelf", "", StorageType.SHELF, "", null));
            schemes.add(new Storage("Box", "", StorageType.BOX_UNINDEXED, "", null));
            schemes.add(new Storage("Tube", "", StorageType.TUBE, "", null));

            plasmidScheme.setSchemes(schemes);
            try {
                StorageManager.update(plasmidScheme);
            } catch (ManagerException e) {
                throw new UtilityException(e);
            }
        }

        // strain
        schemes = new ArrayList<Storage>();
        StorageScheme strainScheme = null;
        try {
            strainScheme = StorageManager.getStorageScheme(DEFAULT_STRAIN_STORAGE_SCHEME_NAME);
        } catch (ManagerException e1) {
            throw new UtilityException(e1);
        }
        if (strainScheme == null) {
            strainScheme = new StorageScheme();
            strainScheme.setLabel(DEFAULT_STRAIN_STORAGE_SCHEME_NAME);
            strainScheme.setRoot(strainRoot);
            schemes.add(new Storage("Freezer", "", StorageType.FREEZER, "", null));
            schemes.add(new Storage("Shelf", "", StorageType.SHELF, "", null));
            schemes.add(new Storage("Box", "", StorageType.BOX_UNINDEXED, "", null));
            schemes.add(new Storage("Tube", "", StorageType.TUBE, "", null));

            strainScheme.setSchemes(schemes);
            try {
                StorageManager.update(strainScheme);
            } catch (ManagerException e) {
                throw new UtilityException(e);
            }
        }
        // parts
        schemes = new ArrayList<Storage>();
        StorageScheme partScheme = null;
        try {
            partScheme = StorageManager.getStorageScheme(DEFAULT_PART_STORAGE_SCHEME_NAME);
        } catch (ManagerException e1) {
            throw new UtilityException(e1);
        }
        if (partScheme == null) {
            partScheme = new StorageScheme();
            partScheme.setLabel(DEFAULT_PART_STORAGE_SCHEME_NAME);
            partScheme.setRoot(partRoot);
            schemes.add(new Storage("Freezer", "", StorageType.FREEZER, "", null));
            schemes.add(new Storage("Shelf", "", StorageType.SHELF, "", null));
            schemes.add(new Storage("Box", "", StorageType.BOX_UNINDEXED, "", null));
            schemes.add(new Storage("Tube", "", StorageType.TUBE, "", null));

            partScheme.setSchemes(schemes);
            try {
                StorageManager.update(partScheme);
            } catch (ManagerException e) {
                throw new UtilityException(e);
            }
        }
        // arabidopsis seeds
        schemes = new ArrayList<Storage>();
        StorageScheme arabidopsisScheme = null;
        try {
            arabidopsisScheme = StorageManager
                    .getStorageScheme(DEFAULT_ARABIDOPSIS_STORAGE_SCHEME_NAME);
        } catch (ManagerException e1) {
            throw new UtilityException(e1);
        }
        if (arabidopsisScheme == null) {
            arabidopsisScheme = new StorageScheme();
            arabidopsisScheme.setLabel(DEFAULT_ARABIDOPSIS_STORAGE_SCHEME_NAME);
            arabidopsisScheme.setRoot(arabidopsisSeedRoot);
            schemes.add(new Storage("Shelf", "", StorageType.SHELF, "", null));
            schemes.add(new Storage("Box", "", StorageType.BOX_UNINDEXED, "", null));
            schemes.add(new Storage("Tube", "", StorageType.TUBE, "", null));

            arabidopsisScheme.setSchemes(schemes);
            try {
                StorageManager.update(arabidopsisScheme);
            } catch (ManagerException e) {
                throw new UtilityException(e);
            }
        }
    }

    /**
     * Check for, and create first admin account
     * 
     * @throws UtilityException
     */
    private static void createAdminAccount() throws UtilityException {
        Account adminAccount = null;
        try {
            adminAccount = AccountManager.getByEmail(adminAccountEmail);
        } catch (ManagerException e) {
            throw new UtilityException(e);
        }

        if (adminAccount == null) {
            adminAccount = new Account();
            adminAccount.setEmail(adminAccountEmail);
            adminAccount.setLastName("");
            adminAccount.setFirstName("");
            adminAccount.setInitials("");
            adminAccount.setInstitution("");
            adminAccount
                    .setPassword(AccountController.encryptPassword(adminAccountDefaultPassword));
            adminAccount.setDescription("Administrator Account");
            adminAccount.setIsSubscribed(0);

            adminAccount.setIp("");
            Date currentTime = Calendar.getInstance().getTime();
            adminAccount.setCreationTime(currentTime);
            adminAccount.setModificationTime(currentTime);
            adminAccount.setLastLoginTime(currentTime);

            try {
                AccountManager.save(adminAccount);
                Moderator adminModerator = new Moderator();
                adminModerator.setAccount(adminAccount);
                AccountManager.saveModerator(adminModerator);
            } catch (ManagerException e) {
                String msg = "Could not create administrator account: " + e.toString();
                Logger.error(msg, e);
            }
        }
    }

    private static void createSystemAccount() throws UtilityException {
        // Check for, and create system account
        Account systemAccount = null;
        try {
            systemAccount = AccountManager.getByEmail(systemAccountEmail);
        } catch (ManagerException e) {
            throw new UtilityException(e);
        }
        if (systemAccount == null) {
            // since system account doesn't exist, initialize a new system account
            systemAccount = new Account();
            systemAccount.setEmail(systemAccountEmail);
            systemAccount.setLastName("");
            systemAccount.setFirstName("");
            systemAccount.setInitials("");
            systemAccount.setInstitution("");
            systemAccount.setPassword("");
            systemAccount.setDescription("System Account");
            systemAccount.setIsSubscribed(0);
            systemAccount.setIp("");
            Date currentTime = Calendar.getInstance().getTime();
            systemAccount.setCreationTime(currentTime);
            systemAccount.setModificationTime(currentTime);
            systemAccount.setLastLoginTime(currentTime);

            try {
                AccountManager.save(systemAccount);
            } catch (ManagerException e) {
                String msg = "Could not create system account: " + e.toString();
                Logger.error(msg, e);
            }
        }
    }

    public static Group createFirstGroup() {
        Group group1 = null;
        try {
            group1 = GroupManager.get(everyoneGroup);

        } catch (ManagerException e) {
            String msg = "Could not get everyone group " + e.toString();
            Logger.info(msg);
        }

        if (group1 == null) {
            Group group = new Group();
            group.setLabel("Everyone");
            group.setDescription("Everyone");
            group.setParent(null);

            group.setUuid(everyoneGroup);
            try {
                GroupManager.save(group);
                Logger.info("Creating everyone group");
                group1 = group;
            } catch (ManagerException e) {
                String msg = "Could not save everyone group: " + e.toString();
                Logger.error(msg, e);
            }
        }

        return group1;
    }

    public static void populatePermissionReadGroup() {
        Group group1 = null;
        try {
            group1 = GroupManager.get(everyoneGroup);
        } catch (ManagerException e) {
            // nothing happens
            Logger.debug(e.toString());
        }
        if (group1 != null) {
            ArrayList<Entry> allEntries = null;
            try {
                allEntries = EntryManager.getAllEntries();
            } catch (ManagerException e1) {
                e1.printStackTrace();
            }
            for (Entry entry : allEntries) {
                try {
                    Set<Group> groups = PermissionManager.getReadGroup(entry);
                    int originalSize = groups.size();
                    groups.add(group1);
                    PermissionManager.setReadGroup(entry, groups);

                    String msg = "updated id:" + entry.getId() + " from " + originalSize + " to "
                            + groups.size() + ".";
                    Logger.info(msg);
                } catch (ManagerException e) {
                    // skip
                    Logger.debug(e.toString());
                }

            }
        }
    }

    public static void normalizeAllFundingSources() throws DAOException {
        ArrayList<Entry> allEntries = null;

        try {
            allEntries = EntryManager.getAllEntries();
        } catch (ManagerException e) {
            e.printStackTrace();
        }

        for (Entry entry : allEntries) {
            Set<EntryFundingSource> entryFundingSources = entry.getEntryFundingSources();

            for (EntryFundingSource entryFundingSource : entryFundingSources) {
                normalizeFundingSources(entryFundingSource.getFundingSource());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void normalizeFundingSources(FundingSource dupeFundingSource) throws DAOException {

        String queryString = "from " + FundingSource.class.getName()
                + " where fundingSource=:fundingSource AND"
                + " principalInvestigator=:principalInvestigator";
        Session session = DAO.newSession();
        Query query = session.createQuery(queryString);
        query.setParameter("fundingSource", dupeFundingSource.getFundingSource());
        query.setParameter("principalInvestigator", dupeFundingSource.getPrincipalInvestigator());
        ArrayList<FundingSource> dupeFundingSources = new ArrayList<FundingSource>();
        try {
            dupeFundingSources = new ArrayList<FundingSource>(query.list());
        } catch (HibernateException e) {
            Logger.error("Could not get funding sources " + e.toString(), e);
        } finally {
            if (session.isOpen()) {
                session.close();
            }
        }
        FundingSource keepFundingSource = dupeFundingSources.get(0);
        for (int i = 1; i < dupeFundingSources.size(); i++) {
            FundingSource deleteFundingSource = dupeFundingSources.get(i);
            // normalize EntryFundingSources
            queryString = "from " + EntryFundingSource.class.getName()
                    + " where fundingSource=:fundingSource";
            session = DAO.newSession();
            query = session.createQuery(queryString);
            query.setParameter("fundingSource", deleteFundingSource);
            List<EntryFundingSource> entryFundingSources = null;
            try {
                entryFundingSources = (query).list();
            } catch (HibernateException e) {
                Logger.error("Could not get funding sources " + e.toString(), e);
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }

            for (EntryFundingSource entryFundingSource : entryFundingSources) {
                try {
                    entryFundingSource.setFundingSource(keepFundingSource);
                    DAO.save(entryFundingSource);
                } catch (DAOException e) {
                    throw e;
                }
            }

            // normalize AccountFundingSources
            queryString = "from " + AccountFundingSource.class.getName()
                    + " where fundingSource=:fundingSource";
            session = DAO.newSession();
            query = session.createQuery(queryString);
            query.setParameter("fundingSource", deleteFundingSource);
            List<AccountFundingSource> accountFundingSources = null;
            try {
                accountFundingSources = query.list();
            } catch (HibernateException e) {
                Logger.error("Could not get funding sources " + e.toString(), e);
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }

            for (AccountFundingSource accountFundingSource : accountFundingSources) {
                accountFundingSource.setFundingSource(keepFundingSource);
                try {
                    DAO.save(accountFundingSource);
                } catch (DAOException e) {
                    String msg = "Could set normalized entry funding source: " + e.toString();
                    Logger.error(msg, e);
                }
            }
            try {
                String temp = deleteFundingSource.getPrincipalInvestigator() + ":"
                        + deleteFundingSource.getFundingSource();
                DAO.delete(deleteFundingSource);
                Logger.info("Normalized funding source: " + temp);
            } catch (DAOException e) {
                String msg = "Could not delete funding source during normalization: "
                        + e.toString();
                Logger.error(msg, e);
            }
        }
    }
}
