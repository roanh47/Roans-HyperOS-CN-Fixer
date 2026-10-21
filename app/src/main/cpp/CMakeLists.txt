cmake_minimum_required(VERSION 3.22.1)
project(keygd C)

add_library(keygd SHARED keygd.c)
target_compile_options(keygd PRIVATE -Os -Wall)
