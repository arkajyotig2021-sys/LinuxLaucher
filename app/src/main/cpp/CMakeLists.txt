cmake_minimum_required(VERSION 3.22.1)
project(novaexec C)

add_library(novaexec SHARED native_process.c)
target_compile_features(novaexec PRIVATE c_std_11)
target_compile_options(novaexec PRIVATE -Wall -Wextra -Werror=return-type)
find_library(log-lib log)
target_link_libraries(novaexec ${log-lib})
