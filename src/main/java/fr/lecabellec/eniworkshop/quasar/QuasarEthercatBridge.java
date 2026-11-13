package fr.lecabellec.eniworkshop.quasar;

/**
 * Bridge contract connecting ENI Workshop with Quasar ({@code resoem::EthercatMasterService}
 * and {@code resoes}) over OPC-UA and ZeroMQ.
 *
 * <p>Requirement: REQ-00008, Feature: FR-00008, Task: TSK-00008.
 */
public interface QuasarEthercatBridge {

    /**
     * Connects to a Quasar OPC-UA server endpoint.
     *
     * @param endpointUrl OPC-UA URL (e.g., "opc.tcp://localhost:4840")
     * @return current {@link QuasarBusStatus}
     */
    QuasarBusStatus connect(String endpointUrl);

    /**
     * Disconnects the active OPC-UA and ZeroMQ sessions cleanly.
     */
    void disconnect();

    /**
     * Invokes {@code refreshStatus} on Quasar's EthercatMasterService.
     *
     * @return refreshed {@link QuasarBusStatus}
     */
    QuasarBusStatus refreshStatus();

    /**
     * Commands all live EtherCAT slaves to INIT via Quasar {@code forceInit}.
     *
     * @return execution status message
     */
    String forceInit();

    /**
     * Reconfigures a specific slave index via Quasar {@code reconfigureSlave}.
     *
     * @param slaveIndex 0-based slave index
     * @return execution status message
     */
    String reconfigureSlave(int slaveIndex);

    /**
     * Scans the live/virtual Quasar EtherCAT bus via {@code exportEni} and imports the resulting ENI.
     *
     * @param masterName master name assigned to generated ENI
     * @param reference  catalog reference when persisted
     * @return {@link QuasarScanResult}
     */
    QuasarScanResult scanAndBuildEni(String masterName, String reference);

    /**
     * Loads and tests a stored ENI configuration on Quasar via {@code loadEni} and {@code compareEni}.
     *
     * @param eniCatalogId stored ENI catalog ID
     * @return {@link QuasarCompareResult}
     */
    QuasarCompareResult pushAndTestEni(String eniCatalogId);
}
