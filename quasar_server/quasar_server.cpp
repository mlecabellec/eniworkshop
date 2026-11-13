/**
 * @file quasar_server.cpp
 * @brief Quasar OPC-UA + resoem EtherCAT master integration helper server for ENI Workshop.
 *
 * Bridges Quasar's OPC-UA server (port 4840 by default) with {@code resoem::EthercatMasterService}
 * so ENI Workshop can scan physical or virtual EtherCAT buses (including resoes simulated slaves)
 * into ETG.2100 ENI files and validate/test generated ENIs against a live stack.
 */

#include "quasar/opcua/OpcUaServerService.hpp"
#include "quasar/named/NamedObject.hpp"
#include "quasar/named/NamedString.hpp"
#include "resoem/EthercatMasterService.hpp"

#include <chrono>
#include <csignal>
#include <cstdlib>
#include <iostream>
#include <memory>
#include <string>
#include <thread>

using namespace quasar::named;
using namespace quasar::opcua;

namespace {

volatile std::sig_atomic_t g_running = 1;

void handleSignal(int signum) {
    std::cout << "[eniworkshop-quasar] Received signal " << signum << ", stopping...\n";
    g_running = 0;
}

} // namespace

/**
 * @brief Entry point launching OpcUaServerService with EthercatMasterService mounted at Root.
 * @param argc argument count
 * @param argv optional NIC name (e.g. "eth0") or "virtual" (default)
 * @return 0 on graceful shutdown
 */
int main(int argc, char* argv[]) {
    std::signal(SIGINT, handleSignal);
    std::signal(SIGTERM, handleSignal);

    const std::string iface = (argc > 1 && argv[1] != nullptr) ? argv[1] : "virtual";
    std::cout << "[eniworkshop-quasar] Starting OPC-UA server (iface=" << iface << ")...\n";

    std::shared_ptr<NamedObject> root = NamedObject::create("Root");
    std::shared_ptr<NamedString> buildTag = NamedString::create("WorkshopBridge", "eniworkshop-quasar-1.0", root);
    (void)buildTag;

    std::shared_ptr<resoem::EthercatMasterService> ecatMaster =
        resoem::EthercatMasterService::create("EthercatMasterService", root);
    ecatMaster->setInterface(iface);

    std::shared_ptr<OpcUaServerService> opcuaServer = OpcUaServerService::create("OpcUaServer");
    opcuaServer->setPort(4840);
    opcuaServer->setRootObject(root);

    opcuaServer->start();
    ecatMaster->start();
    opcuaServer->setCycleTime(std::chrono::milliseconds(10));

    std::cout << "[eniworkshop-quasar] Ready at opc.tcp://localhost:4840 "
              << "(NamedService: EthercatMasterService)\n";

    while (g_running != 0 && opcuaServer->isRunning()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    ecatMaster->stop();
    opcuaServer->stop();
    std::cout << "[eniworkshop-quasar] Stopped cleanly.\n";
    return 0;
}
