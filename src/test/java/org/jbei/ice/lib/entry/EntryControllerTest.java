package org.jbei.ice.lib.entry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jbei.ice.controllers.ControllerFactory;
import org.jbei.ice.controllers.common.ControllerException;
import org.jbei.ice.lib.AccountCreator;
import org.jbei.ice.lib.account.model.Account;
import org.jbei.ice.lib.dao.hibernate.HibernateHelper;
import org.jbei.ice.lib.entry.model.ArabidopsisSeed;
import org.jbei.ice.lib.entry.model.Entry;
import org.jbei.ice.lib.entry.model.Part;
import org.jbei.ice.lib.entry.model.Plasmid;
import org.jbei.ice.lib.entry.model.Strain;
import org.jbei.ice.lib.group.GroupController;
import org.jbei.ice.lib.shared.AutoCompleteField;
import org.jbei.ice.lib.shared.dto.AccountInfo;
import org.jbei.ice.lib.shared.dto.comment.UserComment;
import org.jbei.ice.lib.shared.dto.entry.EntryType;
import org.jbei.ice.lib.shared.dto.entry.PartData;
import org.jbei.ice.lib.shared.dto.entry.PlasmidData;
import org.jbei.ice.lib.shared.dto.entry.StrainData;
import org.jbei.ice.lib.shared.dto.folder.FolderDetails;
import org.jbei.ice.lib.shared.dto.group.GroupInfo;
import org.jbei.ice.lib.shared.dto.group.GroupType;
import org.jbei.ice.lib.shared.dto.permission.PermissionInfo;
import org.jbei.ice.server.InfoToModelFactory;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Hector Plahar
 */
public class EntryControllerTest {

    private EntryController controller;

    @Before
    public void setUp() {
        HibernateHelper.initializeMock();
        HibernateHelper.beginTransaction();
        controller = new EntryController();
    }

    @After
    public void tearDown() {
        HibernateHelper.commitTransaction();
    }

    @Test
    public void testGetMatchingAutoCompleteField() throws Exception {
        for (AutoCompleteField field : AutoCompleteField.values()) {
            Set<String> matches = controller.getMatchingAutoCompleteField(field, "TOKEN", 10);
            Assert.assertNotNull(matches);
        }
    }

    @Test
    public void testGetAllEntryIDs() throws Exception {
        Assert.assertNotNull(controller.getAllEntryIds());
    }

    @Test
    public void testCreateStrainWithPlasmid() throws Exception {
        Account account = AccountCreator.createTestAccount("testCreateStrainWithPlasmid", false);
        try {
            controller.createStrainWithPlasmid(account, null, null, null);
        } catch (ControllerException ce) {
            // expecting ce
        }

        StrainData strainData = new StrainData();
        strainData.setAlias("testStrainAlias");
        strainData.setBioSafetyLevel(1);
        strainData.setGenotypePhenotype("genPhenTest");
        strainData.setHost("testHost");
        strainData.setName("sTrain");
        Strain strain = (Strain) InfoToModelFactory.infoToEntry(strainData);
        Assert.assertNotNull(strain);

        PlasmidData plasmidData = new PlasmidData();
        plasmidData.setName("pLasmid");
        plasmidData.setCircular(true);
        plasmidData.setOriginOfReplication("repOrigin");
        plasmidData.setPromoters("None");
        plasmidData.setBackbone("backbone");
        Plasmid plasmid = (Plasmid) InfoToModelFactory.infoToEntry(plasmidData);
        Assert.assertNotNull(plasmid);

        HashSet<Entry> results = controller.createStrainWithPlasmid(account, strain, plasmid, null);
        Assert.assertNotNull(results);
        Assert.assertEquals("Strain with plasmid creation returned wrong entry count", 2, results.size());
    }

    @Test
    public void testCreateEntry() throws Exception {
        Account account = AccountCreator.createTestAccount("testCreateEntry", false);
        Entry strain = new Strain();
        strain = controller.createEntry(account, strain, null);
        Assert.assertNotNull(strain);
        Assert.assertTrue(strain.getId() > 0);
    }

    @Test
    public void testGet() throws Exception {
        Account account = AccountCreator.createTestAccount("testGet", false);
        Entry plasmid = new Plasmid();
        plasmid = controller.createEntry(account, plasmid, null);
        Entry ret = controller.get(account, plasmid.getId());
        Assert.assertNotNull(ret);
    }

    @Test
    public void testGetByRecordId() throws Exception {
        Account account = AccountCreator.createTestAccount("testGetByRecordId", false);
        ArabidopsisSeed seed = new ArabidopsisSeed();
        seed.setEcotype("ecotype");
        seed.setGeneration(ArabidopsisSeed.Generation.M0);
        seed.setParents("parents");
        seed.setPlantType(ArabidopsisSeed.PlantType.OVER_EXPRESSION);
        seed.setHomozygosity("homozygo");
        controller.createEntry(account, seed, null);
        ArabidopsisSeed ret = (ArabidopsisSeed) controller.getByRecordId(account, seed.getRecordId());
        Assert.assertNotNull(ret);

        Assert.assertEquals("ecotype", ret.getEcotype());
        Assert.assertEquals(ArabidopsisSeed.Generation.M0, ret.getGeneration());
        Assert.assertEquals("parents", ret.getParents());
        Assert.assertEquals(ArabidopsisSeed.PlantType.OVER_EXPRESSION, ret.getPlantType());
        Assert.assertEquals("homozygo", ret.getHomozygosity());
    }

    @Test
    public void testGetByPartNumber() throws Exception {
        Account creator = AccountCreator.createTestAccount("testGetByPartNumber", false);

        PlasmidData data = new PlasmidData();
        data.setType(EntryType.PLASMID);
        data.setBioSafetyLevel(1);
        data.setOriginOfReplication("kanamycin");
        data.setCircular(false);
        data.setOwnerEmail(data.getCreatorEmail());
        data.setOwner(data.getCreator());
        data.setShortDescription("testing");
        data.setStatus("Complete");
        data.setName("pSTC100");

        Entry plasmid = InfoToModelFactory.infoToEntry(data);
        plasmid = controller.createEntry(creator, plasmid);
        String partNumber = plasmid.getOnePartNumber().getPartNumber();
        PartData result = controller.getByPartNumber(creator, partNumber);
        Assert.assertNotNull(result);
        Assert.assertEquals(EntryType.PLASMID, result.getType());
        result = controller.getByPartNumber(creator, "fake");
        Assert.assertNull(result);
    }

    @Test
    public void testUpdate() throws Exception {
        Account creator = AccountCreator.createTestAccount("testUpdate1", false);
        Account account = AccountCreator.createTestAccount("testUpdate2", false);

        // create entry
        PlasmidData data = new PlasmidData();
        data.setType(EntryType.PLASMID);
        data.setBioSafetyLevel(1);
        data.setCreatorEmail(creator.getEmail());
        data.setCreator(creator.getFullName());
        data.setOriginOfReplication("kanamycin");
        data.setCircular(false);
        data.setOwnerEmail(data.getCreatorEmail());
        data.setOwner(data.getCreator());
        data.setShortDescription("testing");
        data.setStatus("Complete");
        data.setName("pSTC100");

        Plasmid plasmid = (Plasmid) InfoToModelFactory.infoToEntry(data);
        Assert.assertNotNull(plasmid);

        // add Write permission for account
        ArrayList<PermissionInfo> permissions = new ArrayList<>();
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.setArticle(PermissionInfo.Article.ACCOUNT);
        permissionInfo.setType(PermissionInfo.Type.WRITE_ENTRY);
        permissionInfo.setArticleId(account.getId());
        permissions.add(permissionInfo);
        plasmid = (Plasmid) controller.createEntry(creator, plasmid, permissions);
        Assert.assertNotNull(plasmid);
        Assert.assertTrue(plasmid.getId() > 0);

        // expect two permissions, write for owner and write for account
        Assert.assertEquals("Unexpected number of permissions", 2, plasmid.getPermissions().size());


        // update with account
        data.setCircular(true);
        data.setRecordId(plasmid.getRecordId());
        Entry existing = controller.getByRecordId(account, data.getRecordId());
        ArrayList<PermissionInfo> p = ControllerFactory.getPermissionController()
                                                       .retrieveSetEntryPermissions(account, plasmid);
        data.setPermissions(p);

        Entry entry = InfoToModelFactory.infoToEntry(data, existing);
        Entry updated = controller.update(account, entry);
        Assert.assertNotNull(updated);
    }

    @Test
    public void testGetByName() throws Exception {
        Account creator = AccountCreator.createTestAccount("testGetByName", false);

        PlasmidData data = new PlasmidData();
        data.setType(EntryType.PLASMID);
        data.setBioSafetyLevel(1);
        data.setOriginOfReplication("kanamycin");
        data.setCircular(false);
        data.setOwnerEmail(data.getCreatorEmail());
        data.setOwner(data.getCreator());
        data.setShortDescription("testing");
        data.setStatus("Complete");
        data.setName("pSTC1000");

        Entry plasmid = InfoToModelFactory.infoToEntry(data);
        plasmid = controller.createEntry(creator, plasmid);
        String name = plasmid.getOneName().getName();
        PartData result = controller.getByUniqueName(creator, name);
        Assert.assertNotNull(result);
        Assert.assertEquals(EntryType.PLASMID, result.getType());
        String partNumber = plasmid.getOnePartNumber().getPartNumber();
        result = controller.getByUniqueName(creator, partNumber);
        Assert.assertNull(result);
    }

    @Test
    public void testAddComment() throws Exception {
        Account creator = AccountCreator.createTestAccount("testAddComment", false);

        PlasmidData data = new PlasmidData();
        data.setType(EntryType.PLASMID);
        data.setBioSafetyLevel(1);
        data.setOriginOfReplication("kanamycin");
        data.setCircular(false);
        data.setOwnerEmail(data.getCreatorEmail());
        data.setOwner(data.getCreator());
        data.setShortDescription("testing");
        data.setStatus("Complete");
        data.setName("pSTC1005");

        Entry plasmid = InfoToModelFactory.infoToEntry(data);
        plasmid = controller.createEntry(creator, plasmid);
        Assert.assertNotNull(plasmid);
        UserComment comment = new UserComment("This is a test");
        comment.setEntryId(plasmid.getId());
        comment = controller.addCommentToEntry(creator, comment);
        Assert.assertNotNull(comment);
        PartData entryInfo = controller.retrieveEntryDetails(creator, plasmid.getId());
        Assert.assertNotNull(entryInfo);
        Assert.assertEquals(1, entryInfo.getComments().size());
    }

    @Test
    public void testHasAttachments() throws Exception {

    }

    @Test
    public void testGetNumberOfVisibleEntries() throws Exception {
        Account account1 = AccountCreator.createTestAccount("testGetNumberOfVisibleEntries1", false);
        PlasmidData data = new PlasmidData();
        data.setType(EntryType.PLASMID);
        data.setBioSafetyLevel(1);
        data.setOriginOfReplication("kanamycin");
        data.setCircular(false);
        data.setOwnerEmail(data.getCreatorEmail());
        data.setOwner(data.getCreator());
        data.setShortDescription("testing");
        data.setStatus("Complete");
        data.setName("pSTC1005123q");
        Entry plasmid = InfoToModelFactory.infoToEntry(data);
        plasmid = controller.createEntry(account1, plasmid);
        Assert.assertNotNull(plasmid);
        long count = controller.getNumberOfVisibleEntries(account1);
        Assert.assertEquals(1, count);

        // add account 1 to group
        GroupController groupController = ControllerFactory.getGroupController();
        GroupInfo newGroup = new GroupInfo();
        newGroup.setLabel("test Group");
        newGroup.setDescription("test Group");
        newGroup.setType(GroupType.PRIVATE);
        newGroup = groupController.createGroup(account1, newGroup);
        Assert.assertNotNull(newGroup);
        Assert.assertTrue(newGroup.getId() > 0);
        ArrayList<AccountInfo> members = new ArrayList<>();
        members.add(Account.toDTO(account1));
        Assert.assertNotNull(groupController.setGroupMembers(account1, newGroup, members));
        PermissionInfo permission = new PermissionInfo();
        permission.setArticle(PermissionInfo.Article.GROUP);
        permission.setArticleId(newGroup.getId());
        permission.setType(PermissionInfo.Type.READ_ENTRY);
        permission.setTypeId(plasmid.getId());
        ControllerFactory.getPermissionController().addPermission(account1, permission);

        count = controller.getNumberOfVisibleEntries(account1);
        Assert.assertEquals(1, count);

        Account account2 = AccountCreator.createTestAccount("testGetNumberOfVisibleEntries2", false);
        Assert.assertEquals(0, controller.getNumberOfVisibleEntries(account2));
    }

    @Test
    public void retrieveVisibleEntries() throws Exception {
        Account account = AccountCreator.createTestAccount("testGetNumberOfVisibleEntries", false);
        GroupInfo info = new GroupInfo();
        info.setLabel("test");
        info.setDescription("test");
        info.setType(GroupType.PRIVATE);
        info.getMembers().add(Account.toDTO(account));
        info = ControllerFactory.getGroupController().createGroup(account, info);
        Assert.assertNotNull(info);

        // when user belongs to a group with permissions for entry account already has
        // access then the bug manifests
        for (int i = 0; i < 50; i += 1) {
            Entry entry = new Part();
            entry.setStatus("Complete");
            entry.setShortDescription("test");
            entry.setBioSafetyLevel(1);
            Assert.assertNotNull(controller.createEntry(account, entry));

            if (i % 2 == 0) {
                PermissionInfo permissionInfo = new PermissionInfo();
                permissionInfo.setArticle(PermissionInfo.Article.GROUP);
                permissionInfo.setType(PermissionInfo.Type.READ_ENTRY);
                permissionInfo.setTypeId(entry.getId());
                permissionInfo.setArticleId(info.getId());
                ControllerFactory.getPermissionController().addPermission(account, permissionInfo);
            }
        }

        // bonus retrieve count
        long count = controller.getNumberOfVisibleEntries(account);
        Assert.assertEquals(50, count);

        FolderDetails details = controller.retrieveVisibleEntries(account, null, false, 0, 50);
        Assert.assertNotNull(details);
        Assert.assertEquals("Wrong number of entries returned for visible count", 50, details.getEntries().size());
    }

    @Test
    public void testGetEntryIdsByOwner() throws Exception {

    }

    @Test
    public void testDelete() throws Exception {
    }

    @Test
    public void testGetOwnerEntryCount() throws Exception {

    }

    @Test
    public void testGetAllEntries() throws Exception {

    }

    @Test
    public void testRetrieveEntriesByIdSetSort() throws Exception {

    }

    @Test
    public void testGetEntriesByIdSet() throws Exception {

    }

    @Test
    public void testRetrieveEntryByType() throws Exception {

    }

    @Test
    public void testRetrieveStrainsForPlasmid() throws Exception {

    }
}
