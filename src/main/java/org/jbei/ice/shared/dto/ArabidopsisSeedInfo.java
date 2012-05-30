package org.jbei.ice.shared.dto;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gwt.user.client.rpc.IsSerializable;

public class ArabidopsisSeedInfo extends EntryInfo {

    public enum Generation implements IsSerializable {
        M0, M1, M2, T0, T1, T2, T3, T4, T5
    }

    public enum PlantType implements IsSerializable {
        EMS("EMS"), OVER_EXPRESSION("Over Expression"), RNAI("RNAi"), REPORTER("Reporter"), T_DNA(
                "T-DNA"), OTHER("Other");

        private String display;

        PlantType(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return this.display;
        }
    }

    private String homozygosity;
    private String ecotype;
    private Date harvestDate;
    private String parents;
    private Generation generation;
    private PlantType plantType;

    public ArabidopsisSeedInfo() {
        super(EntryType.ARABIDOPSIS);
    }

    public static Map<String, String> getGenerationOptionsMap() {
        Map<String, String> resultMap = new LinkedHashMap<String, String>();
        for (Generation generation : Generation.values()) {
            resultMap.put(generation.toString(), generation.toString());
        }

        return resultMap;
    }

    public static Map<String, String> getPlantTypeOptionsMap() {
        Map<String, String> resultMap = new LinkedHashMap<String, String>();

        resultMap.put(PlantType.EMS.toString(), "EMS");
        resultMap.put(PlantType.OVER_EXPRESSION.toString(), "Over Expression");
        resultMap.put(PlantType.RNAI.toString(), "RNAi");
        resultMap.put(PlantType.REPORTER.toString(), "Reporter");
        resultMap.put(PlantType.T_DNA.toString(), "T-DNA");
        resultMap.put(PlantType.OTHER.toString(), "Other");

        return resultMap;
    }

    // getters and setters
    public String getHomozygosity() {
        return homozygosity;
    }

    public void setHomozygosity(String homozygosity) {
        this.homozygosity = homozygosity;
    }

    public String getEcotype() {
        return ecotype;
    }

    public void setEcotype(String ecotype) {
        this.ecotype = ecotype;
    }

    public Date getHarvestDate() {
        return harvestDate;
    }

    public void setHarvestDate(Date harvestDate) {
        this.harvestDate = harvestDate;
    }

    public String getParents() {
        return parents;
    }

    public void setParents(String parents) {
        this.parents = parents;
    }

    public Generation getGeneration() {
        return generation;
    }

    public void setGeneration(Generation generation) {
        this.generation = generation;
    }

    public void setPlantType(PlantType plantType) {
        this.plantType = plantType;
    }

    public PlantType getPlantType() {
        return plantType;
    }

}