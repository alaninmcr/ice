package org.jbei.ice.client.entry.view.detail;

import org.jbei.ice.lib.shared.dto.entry.StrainData;

/**
 * View for displaying strain specific entries
 *
 * @author Hector Plahar
 */

public class StrainInfoView extends EntryInfoView<StrainData> {

    public StrainInfoView(StrainData data) {
        super(data);
    }

    @Override
    protected void addShortFieldValues() {
        addShortField("Genotype/Phenotype", info.getGenotypePhenotype());
        addShortField("Host", info.getLinkifiedHost());
        addShortField("Plasmids", info.getLinkifiedPlasmids());
    }

    @Override
    protected void addLongFields() {
        addLongField("Selection Markers", info.getSelectionMarkers());
    }
}
