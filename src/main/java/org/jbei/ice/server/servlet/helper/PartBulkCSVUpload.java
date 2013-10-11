package org.jbei.ice.server.servlet.helper;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jbei.ice.controllers.ControllerFactory;
import org.jbei.ice.controllers.common.ControllerException;
import org.jbei.ice.lib.account.model.Account;
import org.jbei.ice.lib.bulkupload.BulkUploadController;
import org.jbei.ice.lib.logging.Logger;
import org.jbei.ice.lib.shared.EntryAddType;
import org.jbei.ice.lib.shared.dto.bulkupload.BulkUploadAutoUpdate;
import org.jbei.ice.lib.shared.dto.bulkupload.EntryField;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.StringUtils;

/**
 * CSV uploader for parts
 *
 * @author Hector Plahar
 */
public class PartBulkCSVUpload extends BulkCSVUpload {

    public PartBulkCSVUpload(EntryAddType addType, Account account, Path csvFilePath) {
        super(addType, account, csvFilePath);
    }

    protected boolean isValidHeader(EntryField field) {
        return (field != null && headerFields.contains(field));
    }

    @Override
    protected void populateHeaderFields() {
        headerFields.clear();
        headerFields.add(EntryField.PI);
        headerFields.add(EntryField.FUNDING_SOURCE);
        headerFields.add(EntryField.IP);
        headerFields.add(EntryField.BIOSAFETY_LEVEL);
        headerFields.add(EntryField.NAME);
        headerFields.add(EntryField.ALIAS);
        headerFields.add(EntryField.KEYWORDS);
        headerFields.add(EntryField.SUMMARY);
        headerFields.add(EntryField.NOTES);
        headerFields.add(EntryField.REFERENCES);
        headerFields.add(EntryField.LINKS);
        headerFields.add(EntryField.STATUS);
    }

    @Override
    protected void populateRequiredFields() {
        requiredFields.clear();
        requiredFields.add(EntryField.PI);
        requiredFields.add(EntryField.NAME);
        requiredFields.add(EntryField.SUMMARY);
    }

    @Override
    public String processUpload() {
        // maintains list of fields in the order they are contained in the file
        List<EntryField> fields = new LinkedList<>();

        List<BulkUploadAutoUpdate> updates = new LinkedList<>();

        int rowsProcessed = 0;

        // read file
        try (CSVReader csvReader = new CSVReader(new FileReader(csvFilePath.toString()))) {
            String[] lines;
            while ((lines = csvReader.readNext()) != null) {
                if (fields.isEmpty()) {
                    // assume first line is being processed which is expected to have the field headers
                    for (int i = 0; i < lines.length; i += 1) {
                        String line = lines[i];
                        EntryField field = EntryField.fromString(line);
                        if (!isValidHeader(field)) {
                            return "Error: The selected upload type doesn't support the following field [" + line + "]";
                        }

                        fields.add(i, field);
                    }
                } else {
                    // process values
                    BulkUploadAutoUpdate autoUpdate = new BulkUploadAutoUpdate(EntryAddType.addTypeToType(addType));
                    for (int i = 0; i < lines.length; i += 1) {
                        EntryField field = fields.get(i);
                        autoUpdate.getKeyValue().put(field, lines[i]);
                    }
                    updates.add(autoUpdate);
                    rowsProcessed += 1;
                }
            }
        } catch (IOException io) {
            Logger.error(io);
        }

        // limit for performance reasons
        if (rowsProcessed > 300)
            return "Error: Your file contains too may rows [" + rowsProcessed + "]. Limit is 300";

        // validate to ensure all required fields are present
        String errorString = validate(updates);
        if (!StringUtils.isBlank(errorString))
            return errorString;

        // create actual parts in the registry
        try {
            return Long.toString(createRegistryParts(updates));
        } catch (ControllerException ce) {
            Logger.error(ce);
            return "Error: " + ce.getMessage();
        }
    }

    protected long createRegistryParts(List<BulkUploadAutoUpdate> updates) throws ControllerException {
        BulkUploadController controller = ControllerFactory.getBulkUploadController();
        long bulkUploadId = 0;

        for (BulkUploadAutoUpdate update : updates) {
            if (update.getBulkUploadId() <= 0)
                update.setBulkUploadId(bulkUploadId);

            Logger.info(account.getEmail() + ": " + update.toString());
            update = controller.autoUpdateBulkUpload(account, update, addType);
            if (bulkUploadId == 0)
                bulkUploadId = update.getBulkUploadId();
        }
        return bulkUploadId;
    }

    protected String validate(List<BulkUploadAutoUpdate> updates) {
        for (BulkUploadAutoUpdate update : updates) {
            ArrayList<EntryField> toValidate = new ArrayList<EntryField>(requiredFields);
            for (Map.Entry<EntryField, String> entry : update.getKeyValue().entrySet()) {

                EntryField entryField = entry.getKey();
                String value = entry.getValue();

                if (!requiredFields.contains(entryField))
                    continue;

                toValidate.remove(entryField);

                if (StringUtils.isBlank(value)) {
                    return "Error: \"" + entryField.toString() + "\" is a required field.";
                }
            }

            if (!toValidate.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append("Error: File is missing the following required fields [");
                int i = 0;
                for (EntryField field : toValidate) {
                    if (i > 0)
                        builder.append(",");
                    builder.append(field.toString());
                    i += 1;
                }
                builder.append("]");
                return builder.toString();
            }
        }
        return null;
    }
}