cmake_minimum_required(VERSION 3.16)
project(eniworkshop_quasar_server CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT QUASAR_ROOT)
    set(QUASAR_ROOT "/home/vortigern/git/quasar")
endif()

add_executable(eniworkshop_quasar_server quasar_server.cpp)

target_include_directories(eniworkshop_quasar_server PRIVATE
    ${QUASAR_ROOT}/cmake-projects/opcua/include
    ${QUASAR_ROOT}/cmake-projects/named/include
    ${QUASAR_ROOT}/cmake-projects/coretypes/include
    ${QUASAR_ROOT}/cmake-projects/resoem/include
    ${QUASAR_ROOT}/cmake-projects/resoes/include
    ${QUASAR_ROOT}/cmake-projects/third-party/nlohmann
    ${QUASAR_ROOT}/build/cmake-projects/third-party/open62541/src_generated
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/include
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/plugins/include
    ${QUASAR_ROOT}/cmake-projects/third-party/open62541/arch
)

target_link_directories(eniworkshop_quasar_server PRIVATE
    ${QUASAR_ROOT}/build/lib
    ${QUASAR_ROOT}/build/bin
)

target_link_libraries(eniworkshop_quasar_server PRIVATE
    resoem
    resoes
    quasar_opcua
    quasar_scripting
    quasar_named
    quasar_coretypes
    tinyxml2
    open62541
    pthread
)

set_target_properties(eniworkshop_quasar_server PROPERTIES
    BUILD_RPATH "${QUASAR_ROOT}/build/lib"
)
