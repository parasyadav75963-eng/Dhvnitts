cmake_minimum_required(VERSION 3.22.1)
project("dhwanitts_piper")

# हमारी C++ फाइल को एक शेयर्ड लाइब्रेरी (.so) में बदलना
add_library(piper_wrapper SHARED piper_wrapper.cpp)

# एंड्रॉइड के लॉग सिस्टम को C++ से जोड़ना (ताकि हम Logcat में एरर देख सकें)
find_library(log-lib log)
target_link_libraries(piper_wrapper ${log-lib})
