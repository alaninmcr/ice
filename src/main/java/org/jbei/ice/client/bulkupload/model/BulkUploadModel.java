package org.jbei.ice.client.bulkupload.model;

import java.util.ArrayList;
import java.util.HashMap;

import org.jbei.ice.client.ClientController;
import org.jbei.ice.client.IceAsyncCallback;
import org.jbei.ice.client.bulkupload.events.BulkUploadSubmitEvent;
import org.jbei.ice.client.bulkupload.events.BulkUploadSubmitEventHandler;
import org.jbei.ice.client.bulkupload.events.SavedDraftsEvent;
import org.jbei.ice.client.bulkupload.events.SavedDraftsEventHandler;
import org.jbei.ice.client.collection.add.form.SampleLocation;
import org.jbei.ice.client.entry.display.model.SampleStorage;
import org.jbei.ice.client.event.FeedbackEvent;
import org.jbei.ice.client.exception.AuthenticationException;
import org.jbei.ice.client.service.RegistryServiceAsync;
import org.jbei.ice.lib.shared.EntryAddType;
import org.jbei.ice.lib.shared.dto.PartSample;
import org.jbei.ice.lib.shared.dto.bulkupload.BulkUploadInfo;
import org.jbei.ice.lib.shared.dto.entry.EntryType;
import org.jbei.ice.lib.shared.dto.group.UserGroup;

import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class BulkUploadModel {

    private final RegistryServiceAsync service;
    private final HandlerManager eventBus;
    private final HashMap<EntryType, SampleLocation> locationCache;

    public BulkUploadModel(RegistryServiceAsync service, HandlerManager eventBus) {
        this.service = service;
        this.eventBus = eventBus;
        locationCache = new HashMap<EntryType, SampleLocation>();
    }

    public void retrieveDraftMenuData(final SavedDraftsEventHandler handler) {
        new IceAsyncCallback<ArrayList<BulkUploadInfo>>() {

            @Override
            protected void callService(AsyncCallback<ArrayList<BulkUploadInfo>> callback)
                    throws AuthenticationException {
                service.retrieveUserSavedDrafts(ClientController.sessionId, callback);
            }

            @Override
            public void onSuccess(ArrayList<BulkUploadInfo> result) {
                handler.onDataRetrieval(new SavedDraftsEvent(result));
            }
        }.go(eventBus);
    }

//    public void saveBulkImportDraftData(final EntryAddType type, final String name,
//            final String groupUUID,
//            final BulkUploadDraftSubmitEvent.BulkUploadDraftSubmitEventHandler handler) {
//
//        new IceAsyncCallback<BulkUploadInfo>() {
//
//            @Override
//            protected void callService(AsyncCallback<BulkUploadInfo> callback) throws AuthenticationException {
//                service.saveBulkImportDraft(ClientController.sessionId, name, type, groupUUID, callback);
//            }
//
//            @Override
//            public void onSuccess(BulkUploadInfo result) {
//                handler.onSubmit(new BulkUploadDraftSubmitEvent(result));
//            }
//        }.go(eventBus);
//    }

    public void approvePendingBulkImport(final long draftId, final BulkUploadSubmitEventHandler handler) {
        new IceAsyncCallback<Boolean>() {

            @Override
            protected void callService(AsyncCallback<Boolean> callback) throws AuthenticationException {
                service.approvePendingBulkImport(ClientController.sessionId, draftId, callback);
            }

            @Override
            public void onSuccess(Boolean result) {
                handler.onSubmit(new BulkUploadSubmitEvent(result));
            }
        }.go(eventBus);
    }

    public void submitBulkImportDraft(final long bulkImport, final ArrayList<UserGroup> readGroups,
            final BulkUploadSubmitEventHandler handler) {
        new IceAsyncCallback<Boolean>() {

            @Override
            protected void callService(AsyncCallback<Boolean> callback) throws AuthenticationException {
                service.submitBulkUploadDraft(ClientController.sessionId, bulkImport, readGroups, callback);
            }

            @Override
            public void onSuccess(Boolean result) {
                handler.onSubmit(new BulkUploadSubmitEvent(result));
            }
        }.go(eventBus);
    }

    public void retrieveBulkImport(final long id, final int start, final int limit,
            final SavedDraftsEventHandler handler) {
        new IceAsyncCallback<BulkUploadInfo>() {

            @Override
            protected void callService(AsyncCallback<BulkUploadInfo> callback) throws AuthenticationException {
                service.retrieveBulkImport(ClientController.sessionId, id, start, limit, callback);
            }

            @Override
            public void onSuccess(BulkUploadInfo result) {
                ArrayList<BulkUploadInfo> data = new ArrayList<BulkUploadInfo>();
                data.add(result);
                handler.onDataRetrieval(new SavedDraftsEvent(data));
            }
        }.go(eventBus);
    }

    public RegistryServiceAsync getService() {
        return this.service;
    }

    public HandlerManager getEventBus() {
        return this.eventBus;
    }

    public void retrieveDraftsPendingVerification(final SavedDraftsEventHandler handler) {
        new IceAsyncCallback<ArrayList<BulkUploadInfo>>() {
            @Override
            protected void callService(AsyncCallback<ArrayList<BulkUploadInfo>> callback)
                    throws AuthenticationException {
                service.retrieveDraftsPendingVerification(ClientController.sessionId, callback);
            }

            @Override
            public void onSuccess(ArrayList<BulkUploadInfo> result) {
                handler.onDataRetrieval(new SavedDraftsEvent(result));
            }
        }.go(eventBus);
    }

    public void retrieveStorageSchemes(final EntryAddType type, final NewBulkInput bulkInput,
            final SampleStorage sampleStorage) {

        bulkInput.setSampleLocation(null);  // initialize
        if (type == null || type == EntryAddType.STRAIN_WITH_PLASMID) {
            return;
        }

        final EntryType entryType = EntryAddType.addTypeToType(type);
        SampleLocation cacheLocation = locationCache.get(entryType);
        if (cacheLocation != null) {
            bulkInput.setSampleLocation(cacheLocation);
            if (sampleStorage != null && sampleStorage.getPartSample() != null) {
                String locationId = sampleStorage.getPartSample().getLocationId();
                bulkInput.getSheet().selectSample(type, locationId);
            }
            return;
        }

        service.retrieveStorageSchemes(
                ClientController.sessionId,
                entryType,
                new AsyncCallback<HashMap<PartSample, ArrayList<String>>>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        eventBus.fireEvent(new FeedbackEvent(true, "Failed to retrieve the sample location data."));
                    }

                    @Override
                    public void onSuccess(HashMap<PartSample, ArrayList<String>> result) {
                        if (result == null)
                            return;

                        SampleLocation sampleLocation = new SampleLocation(result);
                        locationCache.put(entryType, sampleLocation);
                        bulkInput.setSampleLocation(sampleLocation);
                        if (sampleStorage != null && sampleStorage.getPartSample() != null) {
                            String locationId = sampleStorage.getPartSample().getLocationId();
                            bulkInput.getSheet().selectSample(type, locationId);
                        }
                    }
                });
    }
}
