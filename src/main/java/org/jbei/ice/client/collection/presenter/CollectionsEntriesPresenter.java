package org.jbei.ice.client.collection.presenter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jbei.ice.client.AbstractPresenter;
import org.jbei.ice.client.AppController;
import org.jbei.ice.client.Page;
import org.jbei.ice.client.RegistryServiceAsync;
import org.jbei.ice.client.collection.ICollectionEntriesView;
import org.jbei.ice.client.collection.menu.CollectionEntryMenu;
import org.jbei.ice.client.collection.menu.CollectionUserMenu;
import org.jbei.ice.client.collection.menu.EntrySelectionModelMenu;
import org.jbei.ice.client.collection.menu.UserCollectionMultiSelect;
import org.jbei.ice.client.collection.table.CollectionEntriesDataTable;
import org.jbei.ice.client.common.EntryDataViewDataProvider;
import org.jbei.ice.client.common.FeedbackPanel;
import org.jbei.ice.client.common.table.DataTable;
import org.jbei.ice.client.common.table.EntryTablePager;
import org.jbei.ice.shared.ColumnField;
import org.jbei.ice.shared.FolderDetails;
import org.jbei.ice.shared.dto.EntryInfo;

import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.cellview.client.ColumnSortEvent.AsyncHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.ProvidesKey;

// TODO : show table of collections on click, change view
public class CollectionsEntriesPresenter extends AbstractPresenter {

    private final RegistryServiceAsync service;
    private final HandlerManager eventBus;
    private final ICollectionEntriesView display;

    private final EntryDataViewDataProvider entryDataProvider;
    private final CollectionEntriesDataTable collectionsDataTable;

    // data providers
    private final ListDataProvider<FolderDetails> userListProvider;
    private final ListDataProvider<FolderDetails> systemListProvider;

    // selection menu
    private final EntrySelectionModelMenu subMenu;
    private final Button addToSubmit;
    private final Button moveToSubmit;

    // feedback panel
    private final FeedbackPanel feedbackPanel;

    public CollectionsEntriesPresenter(RegistryServiceAsync service, HandlerManager eventBus,
            final ICollectionEntriesView display, String param) {

        this.service = service;
        this.eventBus = eventBus;
        this.display = display;
        feedbackPanel = new FeedbackPanel("450px");
        feedbackPanel.setVisible(false);
        display.setFeedback(feedbackPanel);

        // initialize all parameters
        this.collectionsDataTable = new CollectionEntriesDataTable(new EntryTablePager());
        this.userListProvider = new ListDataProvider<FolderDetails>(new KeyProvider());
        this.systemListProvider = new ListDataProvider<FolderDetails>(new KeyProvider());
        this.entryDataProvider = new EntryDataViewDataProvider(collectionsDataTable, service);

        // Collections
        initCollectionsView();

        // selection models used for menus
        initMenus();

        // init text box
        initCreateCollectionHandlers();

        display.setDataView(collectionsDataTable);

        // collection sub menu
        addToSubmit = new Button("Submit");
        moveToSubmit = new Button("Submit");

        UserCollectionMultiSelect add = new UserCollectionMultiSelect(addToSubmit,
                this.userListProvider, new SingleSelectionHandler());
        UserCollectionMultiSelect move = new UserCollectionMultiSelect(moveToSubmit,
                this.userListProvider, new SingleSelectionHandler());

        subMenu = new EntrySelectionModelMenu(add, move);
        this.display.setCollectionSubMenu(subMenu.asWidget());

        // handlers for the collection sub menu
        CollectionEntryAddToFolderHandler addToFolderHandler = new CollectionEntryAddToFolderHandler(
                this.service);
        addToSubmit.addClickHandler(addToFolderHandler);

        // retrieve the referenced folder
        if (param != null)
            retrieveEntriesForFolder(Long.decode(param));
    }

    private void initCreateCollectionHandlers() {
        final TextBox quickAddBox = this.display.getUserCollectionMenu().getQuickAddBox();
        quickAddBox.setVisible(false);

        quickAddBox.addKeyPressHandler(new KeyPressHandler() {

            @Override
            public void onKeyPress(KeyPressEvent event) {
                if (event.getCharCode() != KeyCodes.KEY_ENTER)
                    return;

                if (quickAddBox.getText().isEmpty()) {
                    quickAddBox.setStyleName("entry_input_error");
                    return;
                }

                quickAddBox.setVisible(false);
                saveCollection(quickAddBox.getText());
                display.getUserCollectionMenu().hideQuickText();
            }
        });

        quickAddBox.addFocusHandler(new FocusHandler() {

            @Override
            public void onFocus(FocusEvent event) {
                quickAddBox.setText("");
            }
        });

        // quick edit
        CollectionUserMenu userMenu = display.getUserCollectionMenu();
        userMenu.getQuickEditBox().addBlurHandler(new BlurHandler() {

            @Override
            public void onBlur(BlurEvent event) {
                handle();
            }
        });

        userMenu.getQuickEditBox().addKeyDownHandler(new KeyDownHandler() {

            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() != KeyCodes.KEY_ENTER)
                    return;
                handle();
            }
        });
    }

    private void handle() {
        if (!display.getUserCollectionMenu().getQuickEditBox().isVisible())
            return;

        String oldCollectionName = display.getUserCollectionMenu().getCurrentEditSelection()
                .getName();
        String newName = display.getUserCollectionMenu().getQuickEditBox().getText();

        final FolderDetails currentEdit = display.getUserCollectionMenu().getCurrentEditSelection();

        if (oldCollectionName.equals(newName)) {
            // No change
            display.getUserCollectionMenu().setEditDetail(currentEdit);
        } else {
            // RPC with newName
            // TODO : show busy signal
            currentEdit.setName(newName);
            service.updateFolder(AppController.sessionId, currentEdit.getId(), currentEdit,
                new AsyncCallback<FolderDetails>() {

                    @Override
                    public void onSuccess(FolderDetails result) {
                        display.getUserCollectionMenu().setEditDetail(result);
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        Window.alert("there was an error updating");
                        display.getUserCollectionMenu().setEditDetail(currentEdit);
                    }
                });
        }
    }

    private void saveCollection(String value) {
        if (value == null || value.isEmpty())
            return;

        // TODO : actual save
        service.createUserCollection(AppController.sessionId, value, "",
            new AsyncCallback<FolderDetails>() {

                @Override
                public void onFailure(Throwable caught) {
                    feedbackPanel
                            .setFailureMessage("Error connecting to the server. Please try again.");
                }

                @Override
                public void onSuccess(FolderDetails result) {
                    userListProvider.getList().add(result);
                    display.getUserCollectionMenu().addFolderDetail(result);
                }
            });
    }

    private void initCollectionsView() {

        // collections table view. single view used for all collections
        collectionsDataTable.addColumnSortHandler(new AsyncHandler(collectionsDataTable));
        DataTable<EntryInfo>.DataTableColumn<?> createdField = collectionsDataTable
                .getColumn(ColumnField.CREATED);
        collectionsDataTable.getColumnSortList().push(createdField);
    }

    private void checkAndAddEntryTable(DataTable<EntryInfo> display) {
        if (this.entryDataProvider.getDataDisplays().contains(display))
            return;

        this.entryDataProvider.addDataDisplay(display);
    }

    /**
     * Initializes the selection models used for the menu items
     * by adding the selection change handlers
     */
    private void initMenus() {

        // system collection menu
        final CollectionEntryMenu menu = this.display.getSystemCollectionMenu();
        menu.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                if (!menu.isValidClick(event))
                    return;

                feedbackPanel.setVisible(false);
                retrieveEntriesForFolder(menu.getCurrentSelection());
            }
        });

        // user collection menu
        final CollectionUserMenu userMenu = this.display.getUserCollectionMenu();
        userMenu.addClickHandler(new ClickHandler() {

            @Override
            public void onClick(ClickEvent event) {
                if (!userMenu.isValidClick(event))
                    return;

                feedbackPanel.setVisible(false);
                retrieveEntriesForFolder(userMenu.getCurrentSelection().getId());
            }
        });

        // list of collections for menu
        service.retrieveCollections(AppController.sessionId,
            new AsyncCallback<ArrayList<FolderDetails>>() {

                @Override
                public void onSuccess(ArrayList<FolderDetails> result) {

                    // split into user and system
                    if (result == null || result.isEmpty())
                        return;

                    ArrayList<FolderDetails> userFolders = new ArrayList<FolderDetails>();
                    ArrayList<FolderDetails> systemFolder = new ArrayList<FolderDetails>();
                    for (FolderDetails folder : result) {
                        if (folder.isSystemFolder())
                            systemFolder.add(folder);
                        else
                            userFolders.add(folder);
                    }

                    display.getSystemCollectionMenu().setFolderDetails(systemFolder);
                    display.getUserCollectionMenu().setFolderDetails(userFolders);
                    userListProvider.getList().addAll(userFolders);
                    systemListProvider.getList().addAll(systemFolder);
                }

                @Override
                public void onFailure(Throwable caught) {
                    Window.alert("Error retrieving Collections: " + caught.getMessage());
                }
            });
    }

    private void retrieveEntriesForFolder(long folderId) {
        History.newItem(Page.COLLECTIONS.getLink() + ";id=" + folderId, false);

        service.retrieveEntriesForFolder(AppController.sessionId, folderId,
            new AsyncCallback<ArrayList<Long>>() {

                @Override
                public void onSuccess(ArrayList<Long> result) {
                    if (result == null)
                        return;

                    entryDataProvider.setValues(result);
                    collectionsDataTable.setVisibleRangeAndClearData(
                        collectionsDataTable.getVisibleRange(), false);
                    checkAndAddEntryTable(collectionsDataTable);
                    display.setDataView(collectionsDataTable);
                }

                @Override
                public void onFailure(Throwable caught) {
                    Window.alert("Error: " + caught.getMessage());
                }
            });
    }

    protected void clearDataDisplayFromProviders() {
        if (entryDataProvider.getDataDisplays() == null
                || entryDataProvider.getDataDisplays().isEmpty())
            return;

        for (HasData<EntryInfo> view : entryDataProvider.getDataDisplays()) {
            entryDataProvider.removeDataDisplay(view);
        }
    }

    @Override
    public void go(HasWidgets container) {
        container.clear();
        container.add(this.display.asWidget());
    }

    private class KeyProvider implements ProvidesKey<FolderDetails> {

        @Override
        public Long getKey(FolderDetails item) {
            return item.getId();
        }
    }

    // TODO shares elements with SubmitHandler. 
    // TODO this entire class does not feel right
    private class SingleSelectionHandler implements MultiSelectSelectionHandler {

        private int entrySize;

        @Override
        public void onSingleSelect(FolderDetails details) {
            subMenu.getCollectionMenu().hidePopup();
            HashSet<FolderDetails> folders = new HashSet<FolderDetails>();
            folders.add(details);
            display.getUserCollectionMenu().setBusyIndicator(folders);

            ArrayList<Long> destinationFolderIds = new ArrayList<Long>();
            destinationFolderIds.add(details.getId());

            // TODO : inefficient
            ArrayList<Long> ids = new ArrayList<Long>();
            for (EntryInfo datum : collectionsDataTable.getEntries()) {
                if (datum == null)
                    continue;
                ids.add(Long.decode(datum.getRecordId()));
            }

            entrySize = collectionsDataTable.getEntries().size();

            // service call to actually add
            service.addEntriesToCollection(AppController.sessionId, destinationFolderIds, ids,
                new AsyncCallback<ArrayList<FolderDetails>>() {

                    @Override
                    public void onSuccess(ArrayList<FolderDetails> result) {
                        if (result != null) {
                            display.getUserCollectionMenu().updateCounts(result);
                            String msg = "<b>" + entrySize + "</b> entries successfully added to ";
                            msg += ("\"" + result.get(0).getName() + "\" collection.");
                            feedbackPanel.setSuccessMessage(msg);
                        } else {
                            feedbackPanel
                                    .setFailureMessage("An error occured while connecting to the server.");
                        }
                    }

                    @Override
                    public void onFailure(Throwable caught) {
                        feedbackPanel
                                .setFailureMessage("An error occured while connecting to the server.");
                    }
                });
        }
    }

    // helper class for adding to folder
    class CollectionEntryAddToFolderHandler extends AddToFolderHandler {
        private int entrySize;

        public CollectionEntryAddToFolderHandler(RegistryServiceAsync service) {
            super(service);
        }

        @Override
        public void onClick(ClickEvent event) {
            super.onClick(event);
            subMenu.getCollectionMenu().hidePopup();
            Set<FolderDetails> folders = subMenu.getCollectionMenu().getAddToDestination();
            display.getUserCollectionMenu().setBusyIndicator(folders);
            // TODO : return list of folders that have the busy indicator set to enable easy updateCounts();
        }

        @Override
        protected ArrayList<FolderDetails> getDestination() {
            ArrayList<FolderDetails> list = new ArrayList<FolderDetails>();
            list.addAll(subMenu.getCollectionMenu().getAddToDestination());
            return list;
        }

        @Override
        protected ArrayList<Long> getEntryIds() {
            // TODO : inefficient
            ArrayList<Long> ids = new ArrayList<Long>();
            for (EntryInfo datum : collectionsDataTable.getEntries()) {
                ids.add(Long.decode(datum.getRecordId()));
            }
            entrySize = ids.size();
            return ids;
        }

        @Override
        public void onAddSuccess(ArrayList<FolderDetails> results) {
            display.getUserCollectionMenu().updateCounts(results);
            String msg = "<b>" + entrySize + "</b> entries successfully added to ";
            if (results.size() == 1)
                msg += ("\"" + results.get(0).getName() + "\" collection.");
            else
                msg += (results.size() + " collections.");
            feedbackPanel.setSuccessMessage(msg);
        }

        @Override
        public void onAddFailure(String msg) {
            feedbackPanel.setFailureMessage("An error occurred while adding the entries.");
        }
    };
}
