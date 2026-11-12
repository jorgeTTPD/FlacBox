# Reglas de ofuscación para FlacBox.
# El motor nativo se enlaza por nombre de clase/método: hay que conservarlo.
-keep class dev.jorge.flacbox.audio.NativeAudio { *; }
-keep class dev.jorge.flacbox.audio.EngineCallback { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
