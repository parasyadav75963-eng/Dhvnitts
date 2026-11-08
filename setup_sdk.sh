#!/bin/bash

# Termux में आम तौर पर इस्तेमाल होने वाले SDK रास्ते
COMMON_PATHS=(
    "$HOME/android-sdk" 
    "$HOME/Android/Sdk" 
    "$PREFIX/share/android-sdk" 
    "$PREFIX/opt/android-sdk" 
    "/data/data/com.termux/files/usr/share/android-sdk"
    "$HOME/Android"
)

SDK_FOUND=""

# सही रास्ता ढूंढना
for p in "${COMMON_PATHS[@]}"; do
    if [ -d "$p" ]; then
        SDK_FOUND="$p"
        break
    fi
done

if [ -n "$SDK_FOUND" ]; then
    # local.properties फाइल बनाना
    echo "sdk.dir=$SDK_FOUND" > local.properties
    echo "✅ बहुत बढ़िया! आपका Android SDK यहाँ मिल गया: $SDK_FOUND"
    echo "✅ local.properties फाइल सफलतापूर्वक बन गई है।"
    echo "🚀 चलिए, अब ऐप का बिल्ड फिर से शुरू करते हैं (512MB रैम लिमिट के साथ)..."
    
    chmod +x gradlew
    ./gradlew assembleDebug
else
    echo "❌ माफ़ करना, मुझे Termux के डिफ़ॉल्ट फोल्डर्स में Android SDK नहीं मिला।"
    echo "👉 कृपया मुझे बताएं कि आपने Android SDK किस नाम के फोल्डर में इंस्टॉल किया है (जैसे: ~/my-sdk)?"
fi
