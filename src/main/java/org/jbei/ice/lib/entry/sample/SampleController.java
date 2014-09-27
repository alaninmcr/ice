package org.jbei.ice.lib.entry.sample;

import java.util.ArrayList;
import java.util.List;

import org.jbei.ice.ControllerException;
import org.jbei.ice.lib.access.PermissionException;
import org.jbei.ice.lib.account.AccountTransfer;
import org.jbei.ice.lib.account.model.Account;
import org.jbei.ice.lib.common.logging.Logger;
import org.jbei.ice.lib.dao.DAOException;
import org.jbei.ice.lib.dao.DAOFactory;
import org.jbei.ice.lib.dao.hibernate.SampleDAO;
import org.jbei.ice.lib.dto.StorageInfo;
import org.jbei.ice.lib.dto.sample.PartSample;
import org.jbei.ice.lib.dto.sample.Plate96Sample;
import org.jbei.ice.lib.dto.sample.SampleType;
import org.jbei.ice.lib.dto.sample.ShelfSample;
import org.jbei.ice.lib.entry.EntryAuthorization;
import org.jbei.ice.lib.entry.EntryEditor;
import org.jbei.ice.lib.entry.model.Entry;
import org.jbei.ice.lib.entry.sample.model.Sample;
import org.jbei.ice.lib.models.Storage;

/**
 * ABI to manipulate {@link Sample}s.
 *
 * @author Timothy Ham, Zinovii Dmytriv, Hector Plahar
 */
public class SampleController {

    private final SampleDAO dao;
    private final StorageController storageController;
    private final EntryAuthorization entryAuthorization;

    public SampleController() {
        dao = DAOFactory.getSampleDAO();
        storageController = new StorageController();
        entryAuthorization = new EntryAuthorization();
    }

    /**
     * Save the {@link Sample} into the database, then rebuilds the search index.
     *
     * @param sample
     * @return Saved sample.
     */
    public Sample saveSample(Account account, Sample sample) {
        entryAuthorization.expectWrite(account.getEmail(), sample.getEntry());
        return dao.create(sample);
    }

    /**
     * Delete the {@link Sample} in the database, then rebuild the search index. Also deletes the
     * associated {@link Storage}, if it is a tube.
     *
     * @param sample
     * @throws ControllerException
     * @throws PermissionException
     */
    public void deleteSample(Account account, Sample sample) throws ControllerException, PermissionException {
        entryAuthorization.expectWrite(account.getEmail(), sample.getEntry());

        try {
            Storage storage = sample.getStorage();
            dao.delete(sample);
            if (storage.getStorageType() == Storage.StorageType.TUBE) {
                storageController.delete(storage);
            }
        } catch (DAOException e) {
            throw new ControllerException(e);
        }
    }

    /**
     * Retrieve the {@link Sample}s associated with the {@link Entry}.
     *
     * @param entry
     * @return ArrayList of {@link Sample}s.
     */
    public ArrayList<Sample> getSamples(Entry entry) {
        return dao.getSamplesByEntry(entry);
    }

    /**
     * Retrieve the {@link Sample}s associated with the given {@link Storage}.
     *
     * @param storage
     * @return ArrayList of {@link Sample}s.
     * @throws ControllerException
     */
    public ArrayList<Sample> getSamplesByStorage(Storage storage) throws ControllerException {
        try {
            return dao.getSamplesByStorage(storage);
        } catch (DAOException e) {
            throw new ControllerException(e);
        }
    }

    public boolean hasSample(Entry entry) {
        return dao.hasSample(entry);
    }

    // mainly used by the api to create a strain sample record
    public Sample createStrainSample(Account account, String recordId, String rack, String location, String barcode,
            String label, String strainNamePrefix) throws ControllerException {
        entryAuthorization.expectAdmin(account.getEmail());

        // check if there is an existing sample with barcode
        SampleController sampleController = new SampleController();
        Storage existing = storageController.retrieveStorageTube(barcode.trim());
        if (existing != null) {
            ArrayList<Sample> samples = sampleController.getSamplesByStorage(existing);
            if (samples != null && !samples.isEmpty()) {
                Logger.error("Barcode \"" + barcode + "\" already has a sample associated with it");
                return null;
            }
        }

        // retrieve entry for record id
        Entry entry;
        entry = DAOFactory.getEntryDAO().getByRecordId(recordId);
        if (entry == null)
            throw new ControllerException("Could not locate entry to associate sample with");

        Logger.info("Creating new strain sample [" + rack + ", " + location + ", " + barcode + ", " + label
                            + "] for entry \"" + entry.getId());
        // TODO : this is a hack till we migrate to a single strain default
        Storage strainScheme = null;
        List<Storage> schemes = storageController.retrieveAllStorageSchemes();
        for (Storage storage : schemes) {
            if (storage.getStorageType() == Storage.StorageType.SCHEME
                    && "Strain Storage Matrix Tubes".equals(storage.getName())) {
                strainScheme = storage;
                break;
            }
        }

        if (strainScheme == null) {
            String errMsg = "Could not locate default strain scheme (Strain Storage Matrix Tubes[Plate, Well, Tube])";
            Logger.error(errMsg);
            throw new ControllerException(errMsg);
        }

        Storage newLocation = storageController.getLocation(strainScheme, new String[]{rack, location, barcode});

        Sample sample = SampleCreator.createSampleObject(label, account.getEmail(), "");
        sample.setEntry(entry);
        sample.setStorage(newLocation);
        sample = saveSample(account, sample);
        String name = entry.getName();
        if (strainNamePrefix != null && name != null && !name.startsWith(strainNamePrefix)) {
            new EntryEditor().updateWithNextStrainName(strainNamePrefix, entry);
        }
        return sample;
    }

//    public SampleStorage createSample(Account account, long entryId, SampleStorage sampleStorage) {
//        StorageController storageController = new StorageController();
//
//        Entry entry = DAOFactory.getEntryDAO().get(entryId);
//        if (entry == null) {
//            Logger.error("Could not retrieve entry with id " + entryId + ". Skipping sample creation");
//            return null;
//        }
//
//        entryAuthorization.expectWrite(account.getEmail(), entry);
//
//        PartSample partSample = sampleStorage.getPartSample();
//        LinkedList<StorageInfo> locations = sampleStorage.getStorageList();
//
//        Sample sample = SampleCreator.createSampleObject(partSample.getLabel(), account.getEmail(),
//                                                         partSample.getNotes());
//        sample.setEntry(entry);
//
//        if (locations == null || locations.isEmpty()) {
//            Logger.info("Creating sample without location");
//
//            // create sample, but not location
//            sample = dao.create(sample);
//            sampleStorage.getPartSample().setSampleId(sample.getId() + "");
//            sampleStorage.getPartSample().setDepositor(account.getEmail());
//            return sampleStorage;
//        }
//
//        // create sample and location
//        String[] labels = new String[locations.size()];
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < labels.length; i++) {
//            labels[i] = locations.get(i).getDisplay();
//            sb.append(labels[i]);
//            if (i - 1 < labels.length)
//                sb.append("/");
//        }
//
//        Logger.info("Creating sample with locations " + sb.toString());
//        try {
//            Storage scheme = storageController.get(Long.parseLong(partSample.getLocationId()), false);
//            Storage storage = storageController.getLocation(scheme, labels);
//            storage = storageController.update(storage);
//            sample.setStorage(storage);
//            sample = dao.create(sample);
//            sampleStorage.getStorageList().clear();
//
//            List<Storage> storages = StorageDAO.getStoragesUptoScheme(storage);
//            if (storages != null) {
//                for (Storage storage1 : storages) {
//                    StorageInfo info = new StorageInfo();
//                    info.setDisplay(storage1.getIndex());
//                    info.setId(storage1.getId());
//                    info.setType(storage1.getStorageType().name());
//                    sampleStorage.getStorageList().add(info);
//                }
//            }
//
//            sampleStorage.getPartSample().setSampleId(sample.getId() + "");
//            sampleStorage.getPartSample().setDepositor(account.getEmail());
//            return sampleStorage;
//        } catch (NumberFormatException | DAOException | ControllerException e) {
//            Logger.error(e);
//        }
//
//        return null;
//    }

    public ArrayList<PartSample> retrieveEntrySamples(String userId, long entryId) {
        Entry entry = DAOFactory.getEntryDAO().get(entryId);
        if (entry == null)
            return null;

        entryAuthorization.expectRead(userId, entry);

        // samples
        ArrayList<Sample> entrySamples = dao.getSamplesByEntry(entry);
        ArrayList<PartSample> samples = new ArrayList<>();
        if (entrySamples == null)
            return samples;

        Account userAccount = DAOFactory.getAccountDAO().getByEmail(userId);
        boolean inCart = DAOFactory.getRequestDAO().getSampleRequestInCart(userAccount, entry) != null;

        for (Sample sample : entrySamples) {
            // convert sample to info
            Storage storage = sample.getStorage();
            if (storage == null)
                continue; // sample with no storage

            SampleType type = null;
            StorageInfo well = null;
            StorageInfo tube = null;
            StorageInfo box = null;
            StorageInfo main = null;

            // get the top level type
            while (storage != null) {
                switch (storage.getStorageType()) {
                    case TUBE:
                        tube = storage.toDataTransferObject();
                        break;

                    case WELL:
                        well = storage.toDataTransferObject();
                        break;

                    case BOX_INDEXED:
                        box = storage.toDataTransferObject();
                        break;
                }

                storage = storage.getParent();
                if (storage == null)
                    continue;

                boolean isParent = (type != null && type.isTopLevel());

                if (!isParent && storage.getStorageType() != Storage.StorageType.SCHEME) {
                    type = SampleType.toSampleType(storage.getStorageType().name());
                    if (type.isTopLevel())
                        main = storage.toDataTransferObject();
                }
            }

            // get specific sample type and details about it
            PartSample partSample = sampleFactory(type);
            partSample.setType(type);
            partSample.setCreationTime(sample.getCreationTime().getTime());
            partSample.setLabel(sample.getLabel());
            partSample.setMain(main);
            partSample.setInCart(inCart);

            Account account = DAOFactory.getAccountDAO().getByEmail(sample.getDepositor());
            if (account != null)
                partSample.setDepositor(account.toDataTransferObject());
            else {
                AccountTransfer accountTransfer = new AccountTransfer();
                accountTransfer.setEmail(sample.getDepositor());
                partSample.setDepositor(accountTransfer);
            }

            partSample = setFields(box, well, tube, partSample);
            samples.add(partSample);
        }

        return samples;
    }

    protected static PartSample sampleFactory(SampleType type) {
        switch (type) {
            case PLATE96:
            default:
                return new Plate96Sample();

            case SHELF:
                return new ShelfSample();

        }
    }

    protected static PartSample setFields(StorageInfo box, StorageInfo well, StorageInfo tube, PartSample sample) {
        switch (sample.getType()) {
            case PLATE96:
            default:
                Plate96Sample plate96Sample = (Plate96Sample) sample;
                plate96Sample.setTube(tube);
                plate96Sample.setWell(well);
                return plate96Sample;

            case SHELF:
                ShelfSample shelfSample = (ShelfSample) sample;
                shelfSample.setBox(box);
                shelfSample.setTube(tube);
                shelfSample.setWell(well);
                return shelfSample;
        }
    }
}
