cmake_minimum_required(VERSION 3.22.1)
project(smriti_genie)

# libGenie.so and the QNN backends are resolved at runtime with dlopen, not linked here.
# The devcloud flavour ships none of them, and this way its build does not need them.
add_library(smriti_genie SHARED genie_jni.cpp)

target_compile_options(smriti_genie PRIVATE -Wall -Wextra -fexceptions)
target_link_libraries(smriti_genie log dl)
