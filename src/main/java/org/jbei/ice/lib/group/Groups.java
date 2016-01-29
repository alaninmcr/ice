package org.jbei.ice.lib.group;

import org.jbei.ice.lib.access.PermissionException;
import org.jbei.ice.lib.account.AccountController;
import org.jbei.ice.lib.account.AccountTransfer;
import org.jbei.ice.lib.common.logging.Logger;
import org.jbei.ice.lib.dto.common.Results;
import org.jbei.ice.lib.dto.group.GroupType;
import org.jbei.ice.lib.dto.group.UserGroup;
import org.jbei.ice.lib.utils.Utils;
import org.jbei.ice.storage.DAOFactory;
import org.jbei.ice.storage.hibernate.dao.AccountDAO;
import org.jbei.ice.storage.hibernate.dao.GroupDAO;
import org.jbei.ice.storage.model.Account;
import org.jbei.ice.storage.model.Group;

import java.util.Date;
import java.util.List;

/**
 * Groups of users with methods for manipulation (and access)
 *
 * @author Hector Plahar
 */
public class Groups {

    private final GroupDAO dao;
    private final String userId;

    public Groups(String userId) {
        this.userId = userId;
        this.dao = DAOFactory.getGroupDAO();
    }

    public Results<UserGroup> get(GroupType groupType, int offset, int limit) {
        List<Group> groupList = dao.getGroupsByType(groupType, offset, limit);

        Results<UserGroup> results = new Results<>();
        results.setResultCount(dao.getGroupsByTypeCount(groupType));
        for (Group group : groupList) {
            UserGroup userGroup = group.toDataTransferObject();
            long memberCount = dao.getMemberCount(group.getUuid());
            userGroup.setMemberCount(memberCount);
            results.getData().add(userGroup);
        }
        return results;
    }

    public UserGroup addGroup(UserGroup userGroup) {
        if (userGroup.getType() == null)
            userGroup.setType(GroupType.PRIVATE);

        AccountController accountController = new AccountController();
        if (userGroup.getType() == GroupType.PUBLIC && !accountController.isAdministrator(userId)) {
            String errMsg = "Non admin '" + userId + "' attempting to create public group";
            Logger.error(errMsg);
            throw new PermissionException(errMsg);
        }

        Account account = DAOFactory.getAccountDAO().getByEmail(userId);

        Group group = new Group();
        group.setUuid(Utils.generateUUID());

        group.setLabel(userGroup.getLabel());
        group.setDescription(userGroup.getDescription() == null ? "" : userGroup.getDescription());
        group.setType(userGroup.getType());
        group.setOwner(account);
        group.setAutoJoin(userGroup.isAutoJoin());
        group.setCreationTime(new Date());
        group = dao.create(group);

        if (userGroup.getMembers() != null) {
            AccountDAO accountDAO = DAOFactory.getAccountDAO();

            for (AccountTransfer accountTransfer : userGroup.getMembers()) {
                Account memberAccount = accountDAO.getByEmail(accountTransfer.getEmail());
                if (memberAccount == null)
                    continue;

                memberAccount.getGroups().add(group);
                accountDAO.update(memberAccount);
            }
        }

        return group.toDataTransferObject();
    }
}